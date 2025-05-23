from rich.console import Console
from rich.prompt import Confirm, Prompt
from rich import print as rprint
import os
from pathlib import Path
import argparse
import json
import subprocess
import requests
import re
import traceback
import logging
import sys
import time

# Cache for recent notifications to prevent duplicates
NOTIFICATION_CACHE = set()
CACHE_TIMEOUT = 5  # seconds

# Set up logging function definition
def setup_logging(enabled=True):
    if not enabled:
        logging.getLogger().setLevel(logging.WARNING)
        return
        
    # Use user log file when running as a service
    if args.service_mode:
        log_dir = Path.home() / '.local' / 'log'
        log_dir.mkdir(parents=True, exist_ok=True)
        log_file = log_dir / 'notiforward.log'
    else:
        log_file = 'notiforward.log'
    
    logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s - %(levelname)s - %(message)s',
        handlers=[
            logging.FileHandler(log_file),
            logging.StreamHandler()
        ]
    )

def uninstall():
    try:
        # Stop and disable user-level systemd service
        subprocess.run(['systemctl', '--user', 'stop', 'notiforward.service'], check=False)
        subprocess.run(['systemctl', '--user', 'disable', 'notiforward.service'], check=False)
        
        # Remove user-level service file
        user_service_path = Path.home() / '.config' / 'systemd' / 'user' / 'notiforward.service'
        if user_service_path.exists():
            user_service_path.unlink()
            subprocess.run(['systemctl', '--user', 'daemon-reload'], check=False)

        # Also try to stop and disable system-level service (in case it was previously installed)
        subprocess.run(['sudo', 'systemctl', 'stop', 'notiforward.service'], check=False)
        subprocess.run(['sudo', 'systemctl', 'disable', 'notiforward.service'], check=False)
        
        # Remove system-level service file
        system_service_path = Path('/etc/systemd/system/notiforward.service')
        if system_service_path.exists():
            subprocess.run(['sudo', 'rm', system_service_path], check=False)
            subprocess.run(['sudo', 'systemctl', 'daemon-reload'], check=False)
        
        # Remove script from /opt
        install_path = Path('/opt/notiforward')
        if install_path.exists():
            subprocess.run(['sudo', 'rm', '-rf', install_path], check=False)
        
        # Remove config
        config_path = Path.home() / '.config' / 'notiforward'
        if config_path.exists():
            subprocess.run(['rm', '-rf', config_path], check=False)
            
        # Remove system log file
        system_log_path = Path('/var/log/notiforward.log')
        if system_log_path.exists():
            subprocess.run(['sudo', 'rm', system_log_path], check=False)
        
        # Remove user log file
        user_log_path = Path.home() / '.local' / 'log' / 'notiforward.log'
        if user_log_path.exists():
            user_log_path.unlink()
            
        rprint("[green]✓ Notiforward uninstalled successfully![/green]")
    except Exception as e:
        rprint("[red]! Failed to uninstall[/red]")
        logging.error(f"Uninstall failed: {e}")

def setup_wizard():
    console = Console()
    config_path = Path.home() / '.config' / 'notiforward' / 'config.json'
    
    # Check if already configured
    if config_path.exists():
        return config_path
    
    console.clear()
    rprint("[bold blue]=== Notiforward Setup Wizard ===[/bold blue]\n")
    
    # Create config directory if it doesn't exist
    config_path.parent.mkdir(parents=True, exist_ok=True)
    
    # Get push URL
    endpoint = Prompt.ask(
        "[yellow]Enter the push notification endpoint URL[/yellow]",
        default="https://ntfy.sh/your-topic"
    )
    
    # Ask about logging
    enable_logging = Confirm.ask(
        "[yellow]Enable logging?[/yellow]",
        default=False
    )
    
    # Create config
    config = {
        "endpoint": endpoint,
        "logging": enable_logging
    }
    
    # Save config
    config_path.write_text(json.dumps(config, indent=2))
    
    # Create systemd service if requested
    if Confirm.ask(
        "[yellow]Create systemd service to run on boot?[/yellow]",
        default=True
    ):
        create_systemd_service()
    
    rprint("\n[green]✓ Setup completed successfully![/green]")
    return config_path

def create_systemd_service():
    original_path = Path(__file__).resolve()
    install_path = Path('/opt/notiforward/notiforward.py')
    
    try:
        # Create directory and copy script
        subprocess.run(['sudo', 'mkdir', '-p', '/opt/notiforward'], check=True)
        subprocess.run(['sudo', 'cp', original_path, install_path], check=True)
        subprocess.run(['sudo', 'chown', '-R', f'{os.getenv("USER")}:{os.getenv("USER")}', '/opt/notiforward'], check=True)
        subprocess.run(['sudo', 'chmod', '+x', install_path], check=True)
        
        # Create user service directory if it doesn't exist
        user_service_dir = Path.home() / '.config' / 'systemd' / 'user'
        user_service_dir.mkdir(parents=True, exist_ok=True)
        
        # User-level service content - much simpler than system level
        service_content = f"""[Unit]
Description=Notiforward Discord Notification Forwarder
After=network.target

[Service]
Type=simple
ExecStart={sys.executable} {install_path} --service-mode
WorkingDirectory=/opt/notiforward
Restart=always
RestartSec=10

[Install]
WantedBy=default.target
"""
    
        # Write to user systemd directory instead of system
        service_path = user_service_dir / 'notiforward.service'
        service_path.write_text(service_content)
        
        # Set up user log file
        log_dir = Path.home() / '.local' / 'log'
        log_dir.mkdir(parents=True, exist_ok=True)
        log_path = log_dir / 'notiforward.log'
        log_path.touch(exist_ok=True)
        
        # Use user systemctl commands instead of system ones
        subprocess.run(['systemctl', '--user', 'daemon-reload'], check=True)
        subprocess.run(['systemctl', '--user', 'enable', 'notiforward.service'], check=True)
        subprocess.run(['systemctl', '--user', 'restart', 'notiforward.service'], check=True)
        
        # Enable lingering to allow service to run when user is not logged in
        subprocess.run(['loginctl', 'enable-linger', os.getenv('USER')], check=True)
        
        rprint("[green]✓ User systemd service created and enabled[/green]")
        rprint("[green]✓ Service will start automatically with system boot[/green]")
    except subprocess.CalledProcessError as e:
        rprint("[red]! Failed to enable systemd service[/red]")
        logging.error(f"Systemd service creation failed: {e}")

def load_config(force_setup=False):
    config_path = Path.home() / '.config' / 'notiforward' / 'config.json'
    if force_setup and config_path.exists():
        config_path.unlink()  # Delete existing config
    config_path = setup_wizard()
    return json.loads(config_path.read_text())  # Return the config data, not the path

# Add argument parsing
parser = argparse.ArgumentParser()
parser.add_argument('--re-run-setup', action='store_true', help='Re-run the setup wizard')
parser.add_argument('--service-mode', action='store_true', help='Run in service mode (internal use)')
parser.add_argument('--uninstall', action='store_true', help='Uninstall notiforward completely')
parser.add_argument('--fix-service', action='store_true', help='Fix the systemd service for autostart')
args = parser.parse_args()

# Handle uninstall first, before any other operations
if args.uninstall:
    uninstall()
    sys.exit(0)  # Make sure we exit after uninstall

# Handle service fix if requested
if args.fix_service:
    create_systemd_service()
    sys.exit(0)  # Exit after fixing service

# Handle setup mode
if not args.service_mode:
    config_path = Path.home() / '.config' / 'notiforward' / 'config.json'
    if args.re_run_setup:
        load_config(force_setup=True)
        sys.exit(0)  # Exit after setup
    elif not config_path.exists():
        load_config()  # First time setup
        sys.exit(0)  # Exit after setup
    else:
        rprint("[yellow]Run with --service-mode to start monitoring (this is handled by systemd)[/yellow]")
        rprint("[yellow]Use --re-run-setup to reconfigure or --uninstall to remove[/yellow]")
        sys.exit(0)

# Only continue to main operation if we're in service mode
if not args.service_mode:
    sys.exit(0)

# Normal operation - load config and continue
config = load_config(force_setup=args.re_run_setup)
endpoint = config["endpoint"]

# Now set up logging with the loaded config
setup_logging(config.get("logging", True))

logging.info(f"Loaded endpoint: {endpoint}")

def extract_notification_content(text):
    logging.debug(f"Extracting content from: {text}")
    
    # DBus notifications have a specific format:
    # string "App Name"
    # uint32 ID
    # string "Icon"
    # string "Title/Sender"
    # string "Content/Message"
    
    # Extract all string fields
    string_values = re.findall(r'string "([^"]*)"', text)
    logging.debug(f"Found string values: {string_values}")
    
    # Default values
    title = "Discord"
    content = "New message"
    sender = ""
    
    # For Discord/Vesktop notifications, we expect at least 5 string fields:
    # 0: App name (System Notifications or similar)
    # 1: Icon name
    # 2: Sender/Title (usually the username)
    # 3: Content (the message)
    # The rest are extra fields
    
    if len(string_values) >= 4:
        # The 3rd string value (index 2) is typically the sender/title
        sender = string_values[2]
        # The 4th string value (index 3) is the content
        content = string_values[3]
        
        logging.info(f"Extracted sender: '{sender}' and content: '{content}'")
    
    # Create response with JSON and text versions
    return {
        "json": json.dumps({
            "title": title,
            "content": content,
            "sender": sender,
            "channel_id": "",
            "guild_id": ""
        }),
        "text": f"{sender}: {content}"
    }

def should_ignore_notification(text, content):
    """Check if a notification should be ignored based on content or source"""
    
    # Debug output for all notifications
    logging.info(f"Processing notification with content: '{content}'")
    
    # Only filter out exact matches for debug messages
    if content.lower() == "urgency":
        logging.info("Ignoring vesktop debug notification with content: 'urgency'")
        return True
        
    # Don't filter test messages anymore, they might be legitimate
    # Allow empty content since it might just be a notification without text
    
    return False

def main():
    try:
        logging.info("Starting dbus-monitor process...")
        process = subprocess.Popen(
            ['dbus-monitor', "eavesdrop=true,interface='org.freedesktop.Notifications',member='Notify'"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        
        logging.info("Listening for notifications...")
        
        # Store lines until we have a complete notification
        current_notification = []
        
        while True:
            line = process.stdout.readline()
            if not line:
                logging.error("dbus-monitor process ended unexpectedly")
                break
                
            line = line.strip()
            if not line:
                continue
                
            logging.debug(f"Raw line: {line}")
            current_notification.append(line)
            
            # Check if we have a complete notification
            if line == "]" and len(current_notification) > 1:
                full_notif = "\n".join(current_notification)
                current_notification = []
                
                # Check for Discord or Vesktop
                if ('dev.vencord.Vesktop' in full_notif or 
                    'string "vesktop"' in full_notif or 
                    'string "Discord"' in full_notif or
                    'string "discord"' in full_notif):
                    
                    logging.info("Matched Discord notification!")
                    notification_data = extract_notification_content(full_notif)
                    json_content = notification_data["json"]
                    text_content = notification_data["text"]
                    
                    # Parse the JSON to get the content
                    parsed_json = json.loads(json_content)
                    content = parsed_json.get("content", "")
                    
                    # Skip notification if it should be ignored
                    if should_ignore_notification(full_notif, content):
                        logging.info("Skipping ignored notification")
                        continue
                    
                    # Check if this is a duplicate notification
                    cache_key = f"{text_content}_{int(time.time()) // CACHE_TIMEOUT}"
                    if cache_key in NOTIFICATION_CACHE:
                        logging.info("Skipping duplicate notification")
                        continue
                        
                    # Add to cache and clean old entries
                    current_time = int(time.time())
                    NOTIFICATION_CACHE.add(cache_key)
                    NOTIFICATION_CACHE.difference_update(
                        [k for k in NOTIFICATION_CACHE 
                         if int(k.split('_')[1]) < current_time // CACHE_TIMEOUT - 1]
                    )
                    
                    try:
                        # Log more details about the message
                        logging.info("===== PREPARING TO SEND NOTIFICATION =====")
                        logging.info(f"Raw notification content: {text_content}")
                        logging.info(f"JSON content: {json_content}")
                        
                        # First try sending as text (more compatible with NextPush)
                        logging.info(f"Sending plain text notification first...")
                        res = requests.post(
                            endpoint,
                            data=text_content,
                            headers={"Content-Type": "text/plain"},
                            timeout=10
                        )
                        
                        logging.info(f"Plain text response status: {res.status_code}")
                        
                        # If plain text fails, try JSON
                        if res.status_code > 299:
                            logging.warning(f"Plain text send failed with code: {res.status_code}. Trying JSON format...")
                            res = requests.post(
                                endpoint,
                                data=json_content,
                                headers={"Content-Type": "application/json"},
                                timeout=10
                            )
                            logging.info(f"JSON response status: {res.status_code}")
                        
                        # Print response details
                        logging.info(f"Final response status: {res.status_code}")
                        logging.debug(f"Response text: {res.text}")
                        
                        if res.status_code > 299:
                            logging.error(f"All send attempts failed with code: {res.status_code}\n{res.text}")
                            logging.error("===== NOTIFICATION SENDING FAILED =====")
                        else:
                            logging.info("===== NOTIFICATION SENT SUCCESSFULLY =====")
                    except Exception as e:
                        logging.error(f"Failed to send notification: {e}")
                        logging.error(traceback.format_exc())
                        logging.error("===== NOTIFICATION SENDING FAILED WITH EXCEPTION =====")
                        
                        # As a last resort, try a very simple message
                        try:
                            logging.info("Attempting last resort simple message")
                            requests.post(
                                endpoint,
                                data="New Discord message",
                                headers={"Content-Type": "text/plain"},
                                timeout=10
                            )
                        except Exception as fallback_error:
                            logging.error(f"Even simple fallback failed: {fallback_error}")
                            
    except Exception as e:
        logging.error(f"Failed to start or monitor notifications: {e}")
        logging.error(traceback.format_exc())
        return 1
    
    return 0

if __name__ == "__main__":
    exit(main())

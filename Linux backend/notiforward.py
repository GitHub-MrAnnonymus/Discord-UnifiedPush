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
        
    # Use /var/log/notiforward.log when running as a service
    log_file = '/var/log/notiforward.log' if args.service_mode else 'notiforward.log'
    
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
        # Stop and disable systemd service
        subprocess.run(['sudo', 'systemctl', 'stop', 'notiforward.service'], check=True)
        subprocess.run(['sudo', 'systemctl', 'disable', 'notiforward.service'], check=True)
        
        # Remove systemd service file
        service_path = Path('/etc/systemd/system/notiforward.service')
        if service_path.exists():
            subprocess.run(['sudo', 'rm', service_path], check=True)
            subprocess.run(['sudo', 'systemctl', 'daemon-reload'], check=True)
        
        # Remove script from /opt
        install_path = Path('/opt/notiforward')
        if install_path.exists():
            subprocess.run(['sudo', 'rm', '-rf', install_path], check=True)
        
        # Remove config
        config_path = Path.home() / '.config' / 'notiforward'
        if config_path.exists():
            subprocess.run(['rm', '-rf', config_path], check=True)
            
        # Remove log file
        log_path = Path('/var/log/notiforward.log')
        if log_path.exists():
            subprocess.run(['sudo', 'rm', log_path], check=True)
            
        rprint("[green]✓ Notiforward uninstalled successfully![/green]")
    except subprocess.CalledProcessError as e:
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
        
        service_content = f"""[Unit]
Description=Notiforward Discord Notification Forwarder
After=network.target graphical-session.target
PartOf=graphical-session.target

[Service]
Type=simple
User={os.getenv('USER')}
Group={os.getenv('USER')}
ExecStart={sys.executable} {install_path} --service-mode
WorkingDirectory=/opt/notiforward
Restart=on-failure
RestartSec=10
Environment=PYTHONUNBUFFERED=1
Environment=DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/{os.getuid()}/bus
Environment=DISPLAY=:0
Environment=XDG_RUNTIME_DIR=/run/user/{os.getuid()}
Environment=HOME=/home/{os.getenv('USER')}
Environment=PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
StandardOutput=append:/var/log/notiforward.log
StandardError=append:/var/log/notiforward.log

[Install]
WantedBy=default.target graphical-session.target
"""
    
        service_path = Path('/etc/systemd/system/notiforward.service')
        log_path = Path('/var/log/notiforward.log')
        
        # Create log file and set permissions
        subprocess.run(['sudo', 'touch', log_path], check=True)
        subprocess.run(['sudo', 'chown', f'{os.getenv("USER")}:{os.getenv("USER")}', log_path], check=True)
        
        # Write service file using sudo
        subprocess.run(['sudo', 'tee', service_path], input=service_content, 
                      text=True, check=True, capture_output=True)
        
        subprocess.run(['sudo', 'systemctl', 'daemon-reload'], check=True)
        subprocess.run(['sudo', 'systemctl', 'enable', 'notiforward.service'], check=True)
        subprocess.run(['sudo', 'systemctl', 'restart', 'notiforward.service'], check=True)
        rprint("[green]✓ Systemd service created and enabled[/green]")
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
    # Look for the notification body between the app name and the first array
    body_match = re.search(r'string "([^"]+)"\s+uint32 \d+\s+string "([^"]+)"', text)
    if body_match:
        content = body_match.group(2)
        logging.debug(f"Extracted content: {content}")
        return content
    logging.debug("No content found, using default message")
    return "New Discord notification"

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
                
                # Check for Vesktop
                if 'dev.vencord.Vesktop' in full_notif:
                    logging.info("Matched Vesktop notification!")
                    notification_content = extract_notification_content(full_notif)
                    
                    # Check if this is a duplicate notification
                    cache_key = f"{notification_content}_{int(time.time()) // CACHE_TIMEOUT}"
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
                        logging.info(f"Sending notification: {notification_content}")
                        res = requests.post(
                            endpoint,
                            data=notification_content,
                            headers={"Content-Type": "text/plain"},
                            timeout=10
                        )
                        
                        logging.info(f"Response status: {res.status_code}")
                        logging.debug(f"Response text: {res.text}")
                        
                        if res.status_code > 299:
                            logging.error(f"Send failed with code: {res.status_code}\n{res.text}")
                    except Exception as e:
                        logging.error(f"Failed to send notification: {e}")
                        logging.error(traceback.format_exc())
                            
    except Exception as e:
        logging.error(f"Failed to start or monitor notifications: {e}")
        logging.error(traceback.format_exc())
        return 1
    
    return 0

if __name__ == "__main__":
    exit(main())

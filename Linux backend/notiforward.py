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
import jwt
import base64
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from urllib.parse import urlparse
from pywebpush import webpush, WebPushException

# Cache for recent notifications to prevent duplicates
NOTIFICATION_CACHE = set()
CACHE_TIMEOUT = 5  # seconds

# Docker/Environment Variable Configuration
def load_docker_config():
    """Load configuration from environment variables for Docker deployment."""
    endpoint = os.getenv('NTFY_URL')
    if not endpoint:
        return None
    
    # Validate endpoint format
    if not endpoint.startswith(('http://', 'https://')):
        logging.error(f"Invalid NTFY_URL format: {endpoint}. Must start with http:// or https://")
        return None
        
    enable_logging = os.getenv('ENABLE_LOGGING', 'true').lower() == 'true'
    vapid_enabled = os.getenv('VAPID_ENABLED', 'true').lower() == 'true'
    vapid_auto_generate = os.getenv('VAPID_AUTO_GENERATE', 'true').lower() == 'true'
    
    vapid_config = None
    if vapid_enabled:
        # Check if custom VAPID keys are provided
        custom_private_key = os.getenv('VAPID_PRIVATE_KEY')
        custom_public_key = os.getenv('VAPID_PUBLIC_KEY')
        
        if custom_private_key and custom_public_key:
            # Use provided VAPID keys
            vapid_config = {
                'private_key': custom_private_key,
                'public_key': custom_public_key,
                'vapid_public_key': custom_public_key.split('\n')[1:-1][0] if '-----' in custom_public_key else custom_public_key
            }
            logging.info("Using custom VAPID keys from environment variables")
            
        elif vapid_auto_generate:
            # Auto-generate or load existing VAPID keys
            vapid_keys_dir = Path('/opt/notiforward/keys')
            vapid_keys_file = vapid_keys_dir / 'vapid_keys.json'
            
            if vapid_keys_file.exists():
                # Load existing keys
                try:
                    vapid_config = json.loads(vapid_keys_file.read_text())
                    logging.info("Loaded existing VAPID keys from persistent storage")
                except Exception as e:
                    logging.error(f"Failed to load existing VAPID keys: {e}")
                    vapid_config = None
            
            if not vapid_config:
                # Generate new keys
                vapid_config = generate_vapid_keys()
                if vapid_config:
                    # Ensure directory exists and save keys
                    vapid_keys_dir.mkdir(parents=True, exist_ok=True)
                    vapid_keys_file.write_text(json.dumps(vapid_config, indent=2))
                    logging.info(f"Generated and saved new VAPID keys to {vapid_keys_file}")
                    logging.info(f"VAPID public key: {vapid_config['vapid_public_key']}")
    
    return {
        'endpoint': endpoint,
        'logging': enable_logging,
        'vapid': vapid_config
    }

# VAPID Key Generation and Utilities
def generate_vapid_keys():
    """Generate VAPID key pair for web push authentication."""
    try:
        # Generate EC private key using P-256 curve (required for VAPID)
        private_key = ec.generate_private_key(ec.SECP256R1())
        
        # Get the public key
        public_key = private_key.public_key()
        
        # Serialize private key
        private_pem = private_key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption()
        )
        
        # Serialize public key in the format needed for VAPID
        public_pem = public_key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )
        
        # Convert to base64url format for VAPID
        public_key_bytes = public_key.public_bytes(
            encoding=serialization.Encoding.X962,
            format=serialization.PublicFormat.UncompressedPoint
        )
        
        # Remove the 0x04 prefix and encode as base64url
        vapid_public_key = base64.urlsafe_b64encode(public_key_bytes[1:]).decode('ascii').rstrip('=')
        
        return {
            'private_key': private_pem.decode('ascii'),
            'public_key': public_pem.decode('ascii'), 
            'vapid_public_key': vapid_public_key
        }
    except Exception as e:
        logging.error(f"Failed to generate VAPID keys: {e}")
        return None

def create_vapid_jwt(endpoint_url, vapid_private_key, vapid_public_key):
    """Create VAPID JWT token for authentication."""
    try:
        # Parse endpoint to get audience
        parsed_url = urlparse(endpoint_url)
        audience = f"{parsed_url.scheme}://{parsed_url.netloc}"
        
        # Create JWT payload
        import time
        payload = {
            'aud': audience,
            'exp': int(time.time()) + 43200,  # 12 hours
            'sub': 'mailto:admin@example.com'  # Replace with your contact
        }
        
        # Load private key
        private_key = serialization.load_pem_private_key(
            vapid_private_key.encode('ascii'),
            password=None
        )
        
        # Create JWT
        token = jwt.encode(payload, private_key, algorithm='ES256')
        
        return f"vapid t={token},k={vapid_public_key}"
    except Exception as e:
        logging.error(f"Failed to create VAPID JWT: {e}")
        return None

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
    
    # Ask about VAPID support
    use_vapid = Confirm.ask(
        "[yellow]Enable VAPID authentication? (Recommended for better security and reliability)[/yellow]",
        default=True
    )
    
    vapid_keys = None
    if use_vapid:
        rprint("[blue]Generating VAPID keys for web push authentication...[/blue]")
        vapid_keys = generate_vapid_keys()
        if vapid_keys:
            rprint("[green]✓ VAPID keys generated successfully[/green]")
            rprint(f"[yellow]📋 Your VAPID public key: {vapid_keys['vapid_public_key']}[/yellow]")
            rprint("[blue]🔒 VAPID provides cryptographic authentication for better push delivery[/blue]")
        else:
            rprint("[red]✗ Failed to generate VAPID keys, continuing without VAPID[/red]")
    
    # Create config
    config = {
        "endpoint": endpoint,
        "logging": enable_logging,
        "vapid": vapid_keys
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

# Normal operation - try Docker config first, then fall back to setup wizard
config = load_docker_config()

if config is None:
    # No Docker config found, use traditional setup wizard
    config = load_config(force_setup=args.re_run_setup)
else:
    # Using Docker configuration
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
    logging.info("Using Docker environment variable configuration")
    if config.get('vapid'):
        logging.info("VAPID authentication enabled via Docker configuration")

endpoint = config["endpoint"]

# Set up logging with the loaded config
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
        
        # Check for failed parsing cases and provide fallback message
        if content.lower().strip() == "vesktop:" or content.lower().strip() == "vesktop":
            content = "Failed to parse message content, check Discord"
            logging.warning(f"Detected failed content parsing, using fallback message")
        
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

def send_webpush_notification(endpoint, message, vapid_config=None):
    """Send web push notification with optional VAPID authentication."""
    try:
        if vapid_config:
            # Use VAPID authenticated web push
            logging.info("Sending web push notification with VAPID authentication")
            
            # Create VAPID headers
            vapid_jwt = create_vapid_jwt(
                endpoint, 
                vapid_config['private_key'], 
                vapid_config['vapid_public_key']
            )
            
            if not vapid_jwt:
                logging.error("Failed to create VAPID JWT, falling back to regular HTTP")
                return send_regular_notification(endpoint, message)
            
            # Use pywebpush for proper web push with VAPID
            response = webpush(
                subscription_info={'endpoint': endpoint},
                data=message,
                vapid_private_key=vapid_config['private_key'],
                vapid_claims={'sub': 'mailto:admin@example.com'}
            )
            
            logging.info(f"Web push with VAPID sent successfully")
            return True
            
        else:
            # Fall back to regular HTTP POST
            logging.info("No VAPID config, falling back to regular HTTP POST")
            return send_regular_notification(endpoint, message)
            
    except WebPushException as e:
        logging.error(f"Web push failed: {e}")
        logging.info("Falling back to regular HTTP POST")
        return send_regular_notification(endpoint, message)
    except Exception as e:
        logging.error(f"Unexpected error in web push: {e}")
        logging.info("Falling back to regular HTTP POST")
        return send_regular_notification(endpoint, message)

def send_regular_notification(endpoint, message):
    """Send notification using regular HTTP POST (legacy method)."""
    try:
        # Try both text and JSON formats
        text_content = message if isinstance(message, str) else str(message)
        
        # First try as plain text
        logging.info("Sending as plain text...")
        res = requests.post(
            endpoint,
            data=text_content,
            headers={"Content-Type": "text/plain"},
            timeout=10
        )
        
        logging.info(f"Plain text response status: {res.status_code}")
        
        if res.status_code <= 299:
            return True
            
        # If plain text fails, try as JSON
        try:
            json_data = json.loads(text_content) if isinstance(text_content, str) else text_content
            json_content = json.dumps(json_data)
            
            logging.warning("Plain text failed, trying JSON format...")
            res = requests.post(
                endpoint,
                data=json_content,
                headers={"Content-Type": "application/json"},
                timeout=10
            )
            
            logging.info(f"JSON response status: {res.status_code}")
            return res.status_code <= 299
            
        except (json.JSONDecodeError, TypeError):
            logging.error("Failed to parse message as JSON")
            return False
            
    except Exception as e:
        logging.error(f"Regular notification failed: {e}")
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
                        
                        # Use the new VAPID-enabled web push function
                        vapid_config = config.get("vapid")
                        success = send_webpush_notification(endpoint, text_content, vapid_config)
                        
                        if success:
                            logging.info("===== NOTIFICATION SENT SUCCESSFULLY =====")
                        else:
                            logging.error("===== NOTIFICATION SENDING FAILED =====")
                            
                    except Exception as e:
                        logging.error(f"Failed to send notification: {e}")
                        logging.error(traceback.format_exc())
                        logging.error("===== NOTIFICATION SENDING FAILED WITH EXCEPTION =====")
                        
                        # As a last resort, try a very simple message
                        try:
                            logging.info("Attempting last resort simple message")
                            send_regular_notification(endpoint, "New Discord message")
                        except Exception as fallback_error:
                            logging.error(f"Even simple fallback failed: {fallback_error}")
                            
    except Exception as e:
        logging.error(f"Failed to start or monitor notifications: {e}")
        logging.error(traceback.format_exc())
        return 1
    
    return 0

if __name__ == "__main__":
    exit(main())

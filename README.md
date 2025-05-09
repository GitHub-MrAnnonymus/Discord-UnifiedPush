# Discord-UnifiedPush
This repository hosts an Android app and a Discord notification forwarding system for receiving UnifiedPush notifications for Discord.

**Thanks to charles8191 for making Discord WebView for Android**

## **Backstory**
I am currently using a degoogled Android phone as my daily driver, and apps relying on FCM 
for their notifications became annoying. Most of my apps already support UnifiedPush as a 
protocol, so I implemented it for Discord. I'm pretty new to Android development, and this 
project will be a learning experience as I go (don't expect much).

## **Overview**
This project has two main components:
1. An Android app that uses WebView to render Discord's website
2. A notification forwarder that can run either as a script on Linux or as a Docker container

The system listens to DBus for Discord notifications and forwards them via UnifiedPush to your mobile device.

## **Android App Setup**
1. Download the app-release.apk from the Releases section, or compile it via Android Studio
2. Install and run the app. It will register with a distributor and give you a URL
3. Copy the URL - you'll need it for the backend setup

## **Backend Setup Options**

### Option 1: Docker Container (Recommended)

This approach runs a containerized version of Vesktop with notification forwarding.

1. Clone this repository:
   ```bash
   git clone https://github.com/yourusername/Discord-UnifiedPush.git
   cd Discord-UnifiedPush
   ```

2. Edit the `docker-compose.yml` file to set your UnifiedPush endpoint URL (from the Android app):
   ```yaml
   environment:
     - NTFY_URL=your-unified-push-endpoint-url
     - VNC_PASSWORD=your-secure-password
   ```

3. Start the container:
   ```bash
   docker-compose up -d
   ```

4. Access Vesktop via your web browser at: http://localhost:6080

### Option 2: Linux Script Setup

If you prefer to run the notification forwarder on your existing Linux system:

1. On your Linux machine, run the script found in the "Linux backend" folder of this repository
2. The script will guide you through the setup
3. Paste the URL from the Android app, choose whether you want logs, and whether you want it to run on startup (via a Systemd service)
4. The system will detect Discord notifications from your desktop and forward them to your phone

## **Docker Features**

The Docker container provides:
- Containerized Discord client (Vesktop) accessible via VNC/noVNC in a web browser
- Notification forwarding to your mobile device via UnifiedPush
- Persistent data storage (login credentials are preserved)
- Minimal UI environment with Fluxbox

### Troubleshooting Docker Setup

If you encounter issues with the Docker container:

1. Check the container logs:
   ```bash
   docker logs vesktop-unifiedpush
   ```

2. Examine the process logs within the container:
   ```bash
   docker exec vesktop-unifiedpush cat /var/log/supervisor/vesktop.log
   docker exec vesktop-unifiedpush cat /var/log/supervisor/notiforward.log
   ```

## **Recent Updates**
- **Added Docker Container**: Run Vesktop and notification forwarding in a contained environment
- **Enhanced UnifiedPush Support**: Added robust compatibility with multiple UnifiedPush distributors
- **Automatic Distributor Recovery**: The app includes advanced recovery mechanisms for distributor registration issues
- **NextPush Deep Integration**: Special handling for NextPush with automatic configuration and error recovery
- **Fallback Mechanisms**: If one distributor fails, the app will try alternative distributors
- **Notification Consolidation**: The Android app consolidates multiple notifications to prevent spam
- **Custom Notification Sound**: Discord notifications use the official Discord notification sound

## **Troubleshooting**
### Push Notification Issues
- **NextPush Registration Errors**: If you see a "NextPush internal error" message, try clearing NextPush's storage in Android Settings > Apps > NextPush > Storage > Clear Storage
- **No Endpoint URL**: If no endpoint URL appears after registration, try restarting both the distributor app and Discord-UnifiedPush
- **Multiple Distributors**: The app supports automatic selection between multiple distributors

### Backend Issues (Linux Script)
If the systemd service doesn't auto-start properly after system boot, run this command to fix it:
```bash
python /path/to/notiforward.py --fix-service
```
This will recreate the systemd service with the proper configuration.

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

## **Quick info on WebRTC support**
WebRTC support on Discord can be a bit hit or miss, especially when using mobile browsers like Vanadium. When I tried using it, I noticed the mic and deafen buttons were colored red, meaning they weren’t functional. My solution was to default to loading the Discord website with the user agent set to a generic Android one. However, when I switch my phone to landscape mode, I change the user agent to a generic desktop one. This trick allows WebRTC to work.

The reason for this is that the desktop version of Discord doesn’t play well with mobile screen layouts—it’s not very responsive. So by using this workaround, I can get the desktop version's functionality when the phone is in landscape, but the mobile version's layout when in portrait. In short: Portrait = no WebRTC, Landscape = WebRTC.

-That behavior is present on pretty much all Android browsers not just Vanadium

## **Migration to the latest version that has customizable notifications**
Phone:
1. Clear app data from Discord
2. Go to your UnifiedPush provider and remove Discord

Server (Docker):
1. Remove the containers with
   ```bash
   docker compose down
   ```
2. Remove the docker volume and image, you will need a new image
   ```bash
   docker image rm vesktop-unifiedpush
   ```
3. Remove docker volume (for this one you will have to look yourself, they are all different)
4. Do the setup as usual, just on phone pick what version you want, it's all explained in the setup wizard

Server (script):
1. Run the uninstaller 
   ```bash
   python /path/to/notiforward.py --uninstall
   ```
2. Get the new script from Linux backend folder
3. Run it again and do the setup as usual just on phone pick what version you want, it's all explained in the setup wizard

## **Android App Setup**
1. Download the app-release.apk from the Releases section, or compile it via Android Studio
2. Install and run the app. It will register with a distributor and give you a URL
3. Copy the URL - you'll need it for the backend setup

## **Backend Setup Options**

### Option 1: Docker Container (Recommended)

This approach runs a containerized version of Vesktop with notification forwarding.

1. Clone this repository:
   ```bash
   git clone https://github.com/GitHub-MrAnnonymus/Discord-UnifiedPush.git
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

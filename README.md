# Discord-UnifiedPush
This repository hosts an Android app and its Linux backend for receiving UnifiedPush notifications for Discord.

**Thanks to charles8191 for making Discord WebView for Android**

## **Backstory**
I am currently using a degoogled Android phone as my daily driver, and apps relying on FCM for their notifications became annoying. Most of my apps already support UnifiedPush as a protocol, so I implemented it for Discord. I'm pretty new to Android development, and this project will be a learning experience as I go (don't expect much).

The repository contains an Android app that utilizes the WebView component to render Discord's website. The other components are related to UnifiedPush. The backend is made to run on one of my laptops, which uses Fedora 41 KDE, the system I was testing it on. Your experience may vary. On that laptop, I have Vesktop running as a Flatpak, and the script listens to DBus for notifications. When a notification is detected, it is pushed via the URL provided at setup (in the Android app), and the phone displays it.

## **Installation**
1. Download the app-release.apk from the Releases section, or compile it via Android Studio.
2. Install and run the app. It will register with a distributor and give you a URL.
3. Copy the URL and send it to your "server."
4. On the server, run the script found in the "Linux backend" folder of this repository.
5. The script will guide you through the rest of the setup.
6. Paste the URL, choose whether you want logs, and select if you want it to run on startup (via a Systemd service).
7. That's it! Log in on your Android phone.
8. Profit.

## **Recent Updates**
- **Enhanced UnifiedPush Support**: Added robust compatibility with multiple UnifiedPush distributors including NextPush and NTFY
- **Automatic Distributor Recovery**: The app now includes advanced recovery mechanisms to handle common distributor registration issues
- **NextPush Deep Integration**: Special handling for NextPush with automatic configuration and error recovery
- **Fallback Mechanisms**: If one distributor fails, the app will automatically try alternative distributors
- **Notification Consolidation**: The Android app now consolidates multiple notifications into a single notification in your notification drawer to prevent spam
- **Custom Notification Sound**: Discord notifications now use the official Discord notification sound
- **Fixed Systemd Service**: The systemd service has been improved to properly auto-start with your desktop session

## **Troubleshooting**
### Push Notification Issues
- **NextPush Registration Errors**: If you see a "NextPush internal error" message, try clearing NextPush's storage in Android Settings > Apps > NextPush > Storage > Clear Storage
- **No Endpoint URL**: If no endpoint URL appears after registration, try restarting both the distributor app and Discord-UnifiedPush
- **Multiple Distributors**: The app now supports automatic selection between multiple distributors, with preference for more reliable options

### Backend Issues
If the backend systemd service doesn't auto-start properly after system boot, run this command to fix it:
```bash
python /path/to/notiforward.py --fix-service
```
This will recreate the systemd service with the proper configuration to ensure it starts with your desktop session.

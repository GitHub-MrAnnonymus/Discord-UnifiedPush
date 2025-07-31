#!/bin/bash
set -e

# Use display :2 to avoid conflicts
export DISPLAY_NUM=2
export DISPLAY=:${DISPLAY_NUM}

echo "Starting container with DISPLAY=${DISPLAY}"

# Create necessary directories
mkdir -p /home/appuser/.vnc
mkdir -p /var/log/supervisor
mkdir -p /home/appuser/.config

# Be thorough in cleanup
echo "Cleaning up existing X server resources..."
pkill -f "Xvnc" || true
pkill -f "vncserver" || true
pkill -f "fluxbox" || true
killall -9 Xvnc || true
killall -9 fluxbox || true

# Remove X locks
echo "Removing X locks..."
rm -rf /tmp/.X*-lock
rm -rf /tmp/.X11-unix/X*

# Set up VNC password
echo "Setting up VNC password..."
mkdir -p /home/appuser/.vnc
# Use the environment variable if provided, otherwise use a default password
VNC_PWD=${VNC_PASSWORD:-password}
echo "$VNC_PWD" | vncpasswd -f > /home/appuser/.vnc/passwd
chmod 600 /home/appuser/.vnc/passwd
chown -R appuser:appuser /home/appuser/.vnc

# Clean up any existing D-Bus socket
echo "Setting up D-Bus..."
rm -rf /tmp/dbus
rm -rf /run/dbus/pid

# Set up system D-Bus
mkdir -p /run/dbus
dbus-daemon --system || true

# Wait a moment for system dbus to start
sleep 2

# Start session D-Bus with specific socket path
mkdir -p /tmp/dbus
chown -R appuser:appuser /tmp/dbus
chmod -R 700 /tmp/dbus

# Use a dynamic D-Bus session and save the variables
echo "Starting D-Bus session..."
sudo -u appuser dbus-launch --sh-syntax > /tmp/dbus-session-vars.sh
source /tmp/dbus-session-vars.sh
echo "D-Bus session started at $DBUS_SESSION_BUS_ADDRESS"

# Make sure notification daemon is running (Arch Linux path)
sudo -u appuser /usr/lib/notification-daemon-1.0/notification-daemon &

# Store additional environment variables for other processes
echo "export DBUS_SESSION_BUS_ADDRESS=$DBUS_SESSION_BUS_ADDRESS" > /tmp/dbus-session-vars.sh
echo "export DBUS_SESSION_BUS_PID=$DBUS_SESSION_BUS_PID" >> /tmp/dbus-session-vars.sh
echo "export DISPLAY=:${DISPLAY_NUM}" >> /tmp/dbus-session-vars.sh
echo "export FLATPAK_GL_DRIVERS=dummy" >> /tmp/dbus-session-vars.sh
chmod +x /tmp/dbus-session-vars.sh
chown appuser:appuser /tmp/dbus-session-vars.sh

# Test D-Bus notification
echo "Testing D-Bus notifications..."
sudo -u appuser bash -c "source /tmp/dbus-session-vars.sh && dbus-monitor --session 'type=signal,interface=org.freedesktop.Notifications' --monitor &"
sleep 1

# Update notiforward config with the actual NTFY_URL
if [ ! -z "$NTFY_URL" ]; then
  echo "Setting up notiforward with endpoint: $NTFY_URL"
  CONFIG_FILE="/home/appuser/.config/notiforward/config.json"
  sed -i "s|NTFY_URL_PLACEHOLDER|$NTFY_URL|g" $CONFIG_FILE
  chown appuser:appuser $CONFIG_FILE
fi

# Create a proper xstartup file for VNC
cat > /home/appuser/.vnc/xstartup << 'EOF'
#!/bin/sh
unset SESSION_MANAGER
export XDG_SESSION_TYPE=x11
export XKL_XMODMAP_DISABLE=1

# Source D-Bus session variables if available
if [ -f /tmp/dbus-session-vars.sh ]; then
  source /tmp/dbus-session-vars.sh
fi

# Start a minimal window manager
fluxbox &
EOF

chmod +x /home/appuser/.vnc/xstartup
chown -R appuser:appuser /home/appuser/.vnc

# Set up X11 environment
touch /home/appuser/.Xauthority
chown appuser:appuser /home/appuser/.Xauthority
chown -R appuser:appuser /home/appuser/.config

echo "Starting supervisord..."
# Start supervisord
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
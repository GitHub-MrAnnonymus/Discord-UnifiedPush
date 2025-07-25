FROM fedora:42

# Install system dependencies
RUN dnf update -y && dnf install -y \
    tigervnc-server \
    novnc \
    supervisor \
    xorg-x11-server-Xvfb \
    xorg-x11-xauth \
    xorg-x11-fonts-misc \
    xorg-x11-fonts-Type1 \
    xorg-x11-font-utils \
    xterm \
    libxcb \
    xcb-util \
    mesa-libGL \
    mesa-dri-drivers \
    dbus-x11 \
    dbus-tools \
    notification-daemon \
    libnotify \
    python3 \
    python3-pip \
    python3-dbus \
    python3-setuptools \
    python3-requests \
    python3-cryptography \
    python3-rich \
    python3-urllib3 \
    python3-jwcrypto \
    git \
    procps-ng \
    net-tools \
    fluxbox \
    psmisc \
    strace \
    lsof \
    htop \
    wget \
    curl \
    && dnf clean all

# Create appuser
RUN useradd -m -s /bin/bash appuser

# Download and install Vesktop RPM from vencord.dev
RUN mkdir -p /opt/vesktop && \
    wget -q https://vencord.dev/download/vesktop/amd64/rpm -O /tmp/vesktop.rpm && \
    dnf install -y /tmp/vesktop.rpm && \
    rm /tmp/vesktop.rpm && \
    chown -R appuser:appuser /opt/vesktop || true

# Copy configuration files
COPY supervisord.conf /etc/supervisor/supervisord.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Install notiforward
RUN mkdir -p /opt/notiforward
COPY ["Linux backend/notiforward.py", "/opt/notiforward/"]
RUN cd /opt/notiforward \
    && chown -R appuser:appuser /opt/notiforward

# Create notiforward config
RUN mkdir -p /home/appuser/.config/notiforward && \
    echo '{"endpoint": "NTFY_URL_PLACEHOLDER", "logging": true}' > /home/appuser/.config/notiforward/config.json && \
    chown -R appuser:appuser /home/appuser/.config/notiforward

# Set up Fluxbox
RUN mkdir -p /home/appuser/.fluxbox && \
    echo "session.screen0.toolbar.visible: false" > /home/appuser/.fluxbox/init && \
    chown -R appuser:appuser /home/appuser/.fluxbox

# Create volume mount points for persistent data
RUN mkdir -p /home/appuser/.config/vesktop && \
    chown -R appuser:appuser /home/appuser/.config/vesktop

# Set up runtime directories
RUN mkdir -p /var/log/supervisor \
    && chown -R appuser:appuser /var/log/supervisor

# Expose only the noVNC port (not the raw VNC port)
EXPOSE 6080

# Volume for persistent Vesktop data
VOLUME ["/home/appuser/.config/vesktop"]

# Set entrypoint
ENTRYPOINT ["/entrypoint.sh"] 
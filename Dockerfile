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

# Update pip and core packages first
RUN python3 -m pip install --no-cache-dir --upgrade pip setuptools==78.1.1

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

# Install notiforward with updated secure dependencies
RUN mkdir -p /opt/notiforward
COPY ["Linux backend/notiforward.py", "/opt/notiforward/"]
COPY requirements.txt /opt/notiforward/

# Install all packages with explicit versions and no cache
RUN cd /opt/notiforward \
    && python3 -m pip install --no-cache-dir --upgrade \
        setuptools==78.1.1 \
        urllib3==2.5.0 \
        cryptography==45.0.5 \
        jwcrypto==1.5.6 \
        requests>=2.32.0 \
        rich>=13.0.0 \
    && python3 -m pip install --no-cache-dir -r requirements.txt \
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
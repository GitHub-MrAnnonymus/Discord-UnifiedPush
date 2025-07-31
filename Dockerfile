FROM archlinux:latest

# Update package database and install system dependencies
RUN pacman -Syu --noconfirm && pacman -S --noconfirm \
    tigervnc \
    python-pip \
    supervisor \
    xorg-server \
    xorg-xauth \
    xorg-fonts-misc \
    xterm \
    libxcb \
    xcb-util \
    mesa \
    mesa-utils \
    dbus \
    libnotify \
    notification-daemon \
    python \
    python-setuptools \
    python-requests \
    python-cryptography \
    python-rich \
    python-urllib3 \
    python-jwcrypto \
    python-pyjwt \
    python-dbus \
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
    tar \
    sudo \
    nss \
    && pacman -Scc --noconfirm

# Install packages not available in official repos via pip
RUN pip install --break-system-packages pywebpush websockify

# Install novnc manually since it's not in official repos
RUN git clone https://github.com/novnc/noVNC.git /usr/share/novnc && \
    ln -s /usr/share/novnc/vnc.html /usr/share/novnc/index.html

# Create appuser
RUN useradd -m -s /bin/bash appuser

# Download and install Vesktop from vencord.dev tar archive
RUN mkdir -p /opt/vesktop && \
    wget -q https://vencord.dev/download/vesktop/amd64/tar -O /tmp/vesktop.tar.gz && \
    tar -xzf /tmp/vesktop.tar.gz -C /opt/vesktop --strip-components=1 && \
    rm /tmp/vesktop.tar.gz && \
    chmod +x /opt/vesktop/vesktop && \
    ln -s /opt/vesktop/vesktop /usr/bin/vesktop && \
    chown -R appuser:appuser /opt/vesktop

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

# Create VAPID keys directory for Docker environment variable configuration
RUN mkdir -p /opt/notiforward/keys && \
    chown -R appuser:appuser /opt/notiforward/keys

# Set up runtime directories
RUN mkdir -p /var/log/supervisor \
    && chown -R appuser:appuser /var/log/supervisor

# Expose only the noVNC port (not the raw VNC port)
EXPOSE 6080

# Volume for persistent Vesktop data and VAPID keys
VOLUME ["/home/appuser/.config/vesktop", "/opt/notiforward/keys"]

# Set entrypoint
ENTRYPOINT ["/entrypoint.sh"] 
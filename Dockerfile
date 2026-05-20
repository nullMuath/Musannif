# ─────────────────────────────────────────────────────────────
#  Musannif — JavaFX Desktop App in Docker (VNC access)
#  Works on Windows, macOS, and Linux — no display needed on host
# ─────────────────────────────────────────────────────────────

FROM eclipse-temurin:21-jdk-jammy

# ── System dependencies ────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    # VNC server + virtual framebuffer
    x11vnc \
    xvfb \
    # Lightweight window manager (needed for JavaFX windows to behave)
    openbox \
    # noVNC: browser-based VNC client (no client software needed)
    novnc \
    websockify \
    # JavaFX native libs
    libgtk-3-0 \
    libglib2.0-0 \
    libxtst6 \
    libxxf86vm1 \
    libgl1 \
    libx11-dev \
    # Maven
    maven \
    # Misc
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# ── Clone & build the app ──────────────────────────────────────
WORKDIR /app
RUN git clone --depth=1 https://github.com/cpit252-spring-26-IT2/project-musannif.git .

# Build fat JAR (skip tests so it builds fast and offline-friendly)
RUN mvn package -DskipTests -q

# ── Startup script ────────────────────────────────────────────
COPY start.sh /start.sh
RUN chmod +x /start.sh

# ── Ports ─────────────────────────────────────────────────────
# 5900 = raw VNC  |  6080 = noVNC browser access
EXPOSE 5900 6080

ENTRYPOINT ["/start.sh"]

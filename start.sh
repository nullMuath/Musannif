#!/bin/bash
set -e

DISPLAY_NUM=:1
SCREEN_RES="${SCREEN_RES:-1600x1000x24}"
VNC_PORT="${VNC_PORT:-5900}"
NOVNC_PORT="${NOVNC_PORT:-6080}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Musannif — مُصَنِّف"
echo "  Starting display server..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Ensure the mounted my-files folder exists and is writable
mkdir -p /home/user/files
chmod 777 /home/user/files

# 1. Start virtual framebuffer
Xvfb $DISPLAY_NUM -screen 0 $SCREEN_RES -ac +extension GLX &
sleep 1
export DISPLAY=$DISPLAY_NUM

# 2. Start lightweight window manager
openbox &
sleep 0.5

# 3. Start VNC server — no password
x11vnc \
  -display $DISPLAY_NUM \
  -rfbport $VNC_PORT \
  -nopw \
  -forever \
  -shared \
  -noxdamage \
  -quiet &

sleep 1

# 4. Start noVNC (browser access) — no password
websockify \
  --web /usr/share/novnc \
  $NOVNC_PORT \
  localhost:$VNC_PORT &

sleep 1

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✅ Ready! Open your browser and go to:"
echo ""
echo "     http://localhost:${NOVNC_PORT}/vnc.html"
echo ""
echo "  No password required — just click Connect"
echo "  Generated test files appear in: ./my-files/musannif-test-files/"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 5. Launch the JavaFX app
cd /app
exec java \
  --module-path "$(find ~/.m2/repository/org/openjfx -name '*.jar' | tr '\n' ':')" \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -jar target/Musannif-1.1.0.jar

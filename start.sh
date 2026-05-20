#!/bin/bash
set -e

DISPLAY_NUM=:1
SCREEN_RES="${SCREEN_RES:-1280x800x24}"
VNC_PORT="${VNC_PORT:-5900}"
NOVNC_PORT="${NOVNC_PORT:-6080}"
VNC_PASSWORD="${VNC_PASSWORD:-musannif}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Musannif — مُصَنِّف"
echo "  Starting display server..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. Start virtual framebuffer
Xvfb $DISPLAY_NUM -screen 0 $SCREEN_RES -ac +extension GLX &
sleep 1
export DISPLAY=$DISPLAY_NUM

# 2. Start lightweight window manager
openbox &
sleep 0.5

# 3. Set VNC password
mkdir -p ~/.vnc
x11vnc -storepasswd "$VNC_PASSWORD" ~/.vnc/passwd

# 4. Start VNC server
x11vnc \
  -display $DISPLAY_NUM \
  -rfbport $VNC_PORT \
  -rfbauth ~/.vnc/passwd \
  -forever \
  -shared \
  -noxdamage \
  -quiet &

sleep 1

# 5. Start noVNC (browser access)
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
echo "  VNC Password: ${VNC_PASSWORD}"
echo "  (or connect a VNC client to localhost:${VNC_PORT})"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 6. Launch the JavaFX app
cd /app
exec java \
  --module-path "$(find ~/.m2/repository/org/openjfx -name '*.jar' | tr '\n' ':')" \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -jar target/Musannif-1.1.0.jar

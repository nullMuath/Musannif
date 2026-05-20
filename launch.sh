#!/bin/bash
# ─────────────────────────────────────────────────────────────
#  Musannif Launcher — starts Docker and opens the browser
#  Works on macOS and Linux
# ─────────────────────────────────────────────────────────────

URL="http://localhost:6080/vnc.html"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Starting Musannif..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Start containers in background
docker compose up --build -d

echo ""
echo "  Waiting for the app to be ready..."

# Wait until noVNC responds
until curl -s --max-time 2 "$URL" > /dev/null 2>&1; do
  printf "."
  sleep 2
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✅ App is ready! Opening browser..."
echo "  🔑 Password: musannif"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Open browser — works on macOS and Linux
if command -v xdg-open &> /dev/null; then
  xdg-open "$URL"        # Linux
elif command -v open &> /dev/null; then
  open "$URL"            # macOS
fi

# Now follow the logs so the terminal stays useful
echo ""
echo "  Showing app logs (Ctrl+C to stop logs — app keeps running):"
echo ""
docker compose logs -f

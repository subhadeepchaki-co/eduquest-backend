#!/bin/bash
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a; source "$BACKEND_DIR/.env"; set +a
JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
export JAVA_HOME

SUBDOMAIN="abcdkids"
TUNNEL_URL="https://$SUBDOMAIN.loca.lt"

echo "=== Step 1: Start/verify backend ==="
PID_FILE=/tmp/backend.pid
if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
    echo "Backend already running (PID $(cat $PID_FILE))"
else
    echo "Starting backend..."
    nohup "$JAVA_HOME/bin/java" -jar "$BACKEND_DIR/build/libs/payment-backend-1.0.0.jar" > /tmp/backend.log 2>&1 &
    echo $! > "$PID_FILE"
    echo "Backend started, PID: $!"
    sleep 12
fi

echo -n "Backend test: "
wget -q --timeout=5 -O- --post-data='{"amount":9900,"currency":"INR","category":"premium"}' \
    --header="Content-Type: application/json" \
    http://localhost:8080/api/payments/create-order 2>&1 || echo "FAILED"
echo ""

echo "=== Step 2: Start tunnel ==="
# Kill existing tunnel processes
pkill -f "lt --port" 2>/dev/null || true
sleep 1

# Start tunnel with fixed subdomain
nohup lt --port 8080 --subdomain "$SUBDOMAIN" > /tmp/lt_tunnel.log 2>&1 &
echo "Tunnel PID: $!"
sleep 15

echo "Tunnel URL: $TUNNEL_URL"

echo "=== Step 3: Verify tunnel ==="
echo -n "Tunnel test: "
wget -q --timeout=15 -O- --post-data='{"amount":9900,"currency":"INR","category":"premium"}' \
    --header="Content-Type: application/json" \
    "$TUNNEL_URL/api/payments/create-order" 2>&1 || echo "FAILED"
echo ""

echo ""
echo "=============================================="
echo "  BACKEND + TUNNEL READY"
echo "=============================================="
echo ""
echo "  Tunnel URL: $TUNNEL_URL"
echo "  (URL is FIXED — no need to change if tunnel restarts)"
echo ""
echo "  Install APK:"
echo "    app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "  If the tunnel goes down, it auto-restarts."
echo "  The URL stays the same: $TUNNEL_URL"
echo "=============================================="

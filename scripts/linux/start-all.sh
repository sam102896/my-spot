#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

cleanup() {
  if [ -n "${BACK_PID:-}" ] && kill -0 "$BACK_PID" 2>/dev/null; then
    kill "$BACK_PID" 2>/dev/null || true
  fi
  if [ -n "${WEB_PID:-}" ] && kill -0 "$WEB_PID" 2>/dev/null; then
    kill "$WEB_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT INT TERM

"$ROOT/scripts/linux/start-backend.sh" &
BACK_PID=$!

"$ROOT/scripts/linux/start-web.sh" &
WEB_PID=$!

echo "Backend: http://localhost:3001"
echo "Web: http://localhost:5173"

wait "$BACK_PID" "$WEB_PID"


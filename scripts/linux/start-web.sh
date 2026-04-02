#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../web"

if [ ! -d "node_modules" ]; then
  npm install --no-fund --no-audit
fi

npm run dev


#!/usr/bin/env bash
set -euo pipefail

DATA_DIR="$(cd "$(dirname "$0")/../.." && pwd)/backend/data"

if [ -d "$DATA_DIR" ]; then
  rm -rf "$DATA_DIR"
  echo "Removed: $DATA_DIR"
else
  echo "No data dir: $DATA_DIR"
fi


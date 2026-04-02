#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

mvn -q -pl perf -am package

java -Djmh.ignoreLock=true -jar ./perf/target/my-spot-perf.jar "$@"

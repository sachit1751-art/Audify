#!/usr/bin/env bash
# Copyright (c) 2026 Sachit. All rights reserved.
# Project: AUDIFY
# Audify (Sachit Music) — build APKs for release
# Double-click build-apks.bat on Windows, or run this directly on macOS/Linux.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

DIST="$PROJECT_ROOT/dist"
FLAVOR="${FLAVOR:-prod}"
WITH_DEBUG="${WITH_DEBUG:-false}"

mkdir -p "$DIST"

echo "=== Building Audify $FLAVOR release ==="
./gradlew "assemble${FLAVOR^}Release" \
  -Pandroid.injected.buildCacheDir="$DIST/.build-cache" \
  --no-daemon \
  --console=plain \
  -Paudify.bundleWear=false

if [ "$WITH_DEBUG" = "true" ]; then
  echo "=== Building Audify $FLAVOR debug ==="
  ./gradlew "assemble${FLAVOR^}Debug" \
    -Pandroid.injected.buildCacheDir="$DIST/.build-cache" \
    --no-daemon \
    --console=plain \
    -Paudify.bundleWear=false
fi

echo "=== Done. APKs are in app/build/outputs/apk/$FLAVOR/ ==="
find app/build/outputs/apk -name "*.apk" -type f 2>/dev/null || true

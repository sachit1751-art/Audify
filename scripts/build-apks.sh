#!/usr/bin/env bash
# Audify (Sachit Music) — build APKs for release
# Double-click build-apks.bat on Windows, or run this directly on macOS/Linux.
set -euo pipefail

DIST="dist"
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

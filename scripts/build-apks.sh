#!/usr/bin/env bash
# Audify (Sachit Music) — one-click local APK builder.
#
# Builds distributable APKs from the current working tree:
#   1. :app:assembleFossRelease   -> Audify-foss   (F-Droid/Izzy-style, no Play Services)
#   2. :app:assembleGmsRelease    -> Audify-gms    (with Google Cast)
#   3. :app:assembleIzzyRelease   -> Audify-izzy   (no Play Services, no updater)
#   4. :app:assembleFossDebug     -> Audify-foss debug (debug-signed, installable side-by-side)
#
# By default only the three release APKs are built; pass --with-debug to add
# the debug APK, or --flavor foss|gms|izzy to build a single flavor.
# Outputs are copied (never moved) into dist/<version>-<shortsha>/ with
# unambiguous names.
#
# Usage:
#   scripts/build-apks.sh                     # build all release flavors
#   scripts/build-apks.sh --with-debug        # also build the foss debug APK
#   scripts/build-apks.sh --flavor foss       # build one flavor's release APK
#   scripts/build-apks.sh --with-tests        # run unit tests first, abort on failure
#
# Exit codes: 0 success, 1 preflight failure, 2 build failure.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WITH_TESTS=0
WITH_DEBUG=0
FLAVOR="all"
while [ $# -gt 0 ]; do
  case "$1" in
    --with-tests) WITH_TESTS=1 ;;
    --with-debug) WITH_DEBUG=1 ;;
    --flavor=*)   FLAVOR="${1#--flavor=}" ;;
    --flavor)     shift; FLAVOR="${1:-all}" ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
  shift
done

log()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mWARN:\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. JDK: the project builds with Java 21 (jvmToolchain(21)); accept 21 only
#    so the toolchain matches what CI uses. Scans the usual locations on
#    Windows, including IntelliJ-downloaded JDKs in ~/.jdks.
# ---------------------------------------------------------------------------
CANDIDATE_JDKS=(
  "${JAVA_HOME:-}"
  "C:/Users/HP/.jdks/jbr-21.0.11"
  "C:/Program Files/Android/Android Studio/jbr"
  "C:/Program Files/Android/Android Studio/jbrs/default"
  "C:/Program Files/Java/jdk-21"
  "$HOME/.jdks/jbr-21.0.11"
)
for jdk_dir in "$HOME"/.jdks/*/; do
  [ -d "$jdk_dir" ] || continue
  CANDIDATE_JDKS+=("${jdk_dir%/}")
done

JDK_OK=""
for candidate in "${CANDIDATE_JDKS[@]}"; do
  [ -n "$candidate" ] || continue
  java_bin="$candidate/bin/java"
  [ -f "$java_bin" ] || [ -f "${java_bin}.exe" ] || continue
  version="$("$java_bin" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  case "$version" in
    21) JDK_OK="$candidate"; break ;;
    *) warn "JDK $version at $candidate will not be used (need 21)..." ;;
  esac
done
[ -n "$JDK_OK" ] || fail "No JDK 21 found. Install one or set JAVA_HOME."
export JAVA_HOME="$JDK_OK"
log "Using JDK: $JAVA_HOME"

# ---------------------------------------------------------------------------
# 2. Android SDK: local.properties (sdk.dir) or ANDROID_HOME. If neither is
#    set but the default Windows SDK exists, write local.properties once.
# ---------------------------------------------------------------------------
DEFAULT_SDK="C:/Users/HP/AppData/Local/Android/Sdk"
if [ ! -f "$ROOT/local.properties" ] && [ -z "${ANDROID_HOME:-}" ] && [ -d "$DEFAULT_SDK" ]; then
  printf 'sdk.dir=%s\n' "$DEFAULT_SDK" > "$ROOT/local.properties"
  warn "local.properties was missing; created it pointing at the default SDK."
fi
if [ ! -f "$ROOT/local.properties" ] && [ -z "${ANDROID_HOME:-}" ]; then
  fail "Neither local.properties (sdk.dir) nor ANDROID_HOME is set."
fi
log "Android SDK: ${ANDROID_HOME:-from local.properties}"

# ---------------------------------------------------------------------------
# 3. Signing: the release signing config reads STORE_PASSWORD / KEY_ALIAS /
#    KEY_PASSWORD from the environment. Without them Gradle still assembles
#    release APKs but they will be unsigned (or the task fails, in which case
#    the failure surfaces below with Gradle's own message).
# ---------------------------------------------------------------------------
if [ -z "${STORE_PASSWORD:-}" ] || [ -z "${KEY_PASSWORD:-}" ]; then
  warn "STORE_PASSWORD/KEY_PASSWORD not set -> release APKs will be UNSIGNED."
  warn "Export them (or run inside a shell that has them) for signed builds."
fi

# ---------------------------------------------------------------------------
# 4. Optional test gate.
# ---------------------------------------------------------------------------
if [ "$WITH_TESTS" -eq 1 ]; then
  log "Running unit tests (foss debug)..."
  ./gradlew --console=plain -q :app:testFossDebugUnitTest \
    || fail "Unit tests failed — aborting (run without --with-tests to skip)."
  log "All tests passed."
fi

# ---------------------------------------------------------------------------
# 5. Build.
# ---------------------------------------------------------------------------
TASKS=()
case "$FLAVOR" in
  all)
    TASKS+=(":app:assembleFossRelease" ":app:assembleGmsRelease" ":app:assembleIzzyRelease")
    if [ "$WITH_DEBUG" -eq 1 ]; then TASKS+=(":app:assembleFossDebug"); fi
    ;;
  foss)
    TASKS+=(":app:assembleFossRelease")
    if [ "$WITH_DEBUG" -eq 1 ]; then TASKS+=(":app:assembleFossDebug"); fi
    ;;
  gms)   TASKS+=(":app:assembleGmsRelease") ;;
  izzy)  TASKS+=(":app:assembleIzzyRelease") ;;
  *) fail "Unknown flavor: $FLAVOR (expected foss, gms, izzy or all)" ;;
esac

log "Building: ${TASKS[*]}"
./gradlew --console=plain "${TASKS[@]}" \
  || fail "Gradle build failed (exit $?)."

# ---------------------------------------------------------------------------
# 6. Collect outputs with unambiguous names.
# ---------------------------------------------------------------------------
VERSION=$(grep -E '^\s*versionName = "' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
[ -n "$VERSION" ] || fail "Could not read versionName from app/build.gradle.kts"
SHORT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "nogit")
DIST="$ROOT/dist/${VERSION}-${SHORT_SHA}"
mkdir -p "$DIST"

copy_apk() { # copy_apk <glob> <final-name>
  local found=""
  for f in $1; do
    [ -f "$f" ] || continue
    cp -f "$f" "$DIST/$2"
    log "  -> $DIST/$2"
    found=1
  done
  [ -n "$found" ] || fail "Expected APK not found: $1"
}

if [ "$FLAVOR" = "all" ] || [ "$FLAVOR" = "foss" ]; then
  copy_apk "app/build/outputs/apk/foss/release/*.apk" "Audify-foss-v${VERSION}.apk"
fi
if [ "$FLAVOR" = "all" ] || [ "$FLAVOR" = "gms" ]; then
  copy_apk "app/build/outputs/apk/gms/release/*.apk" "Audify-gms-v${VERSION}.apk"
fi
if [ "$FLAVOR" = "all" ] || [ "$FLAVOR" = "izzy" ]; then
  copy_apk "app/build/outputs/apk/izzy/release/*.apk" "Audify-izzy-v${VERSION}.apk"
fi
if [ "$WITH_DEBUG" -eq 1 ]; then
  copy_apk "app/build/outputs/apk/foss/debug/*.apk" "Audify-foss-debug-v${VERSION}.apk"
fi

echo
log "All APKs built successfully: $DIST"

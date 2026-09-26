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
# Release signing: credentials are read from local.properties
# (AUDIFY_RELEASE_STORE_PASSWORD / AUDIFY_RELEASE_KEY_ALIAS /
# AUDIFY_RELEASE_KEY_PASSWORD, written next to the keystore) or from the
# STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD environment variables. Without
# credentials the script aborts before building — an unsigned release APK
# cannot be installed ("App not installed as package appears to be invalid").
# Every produced APK is additionally verified with apksigner before it is
# collected into dist/.
#
# With --upload, the built APKs are also attached to a GitHub prerelease
# tagged v<version>+<shortsha> (created if needed) using the GitHub
# credentials stored in the local git credential manager — no gh CLI needed.
# Identical assets already on the release are skipped; stale/orphaned assets
# with the same name are replaced.
#
# Usage:
#   scripts/build-apks.sh                     # build all release flavors
#   scripts/build-apks.sh --with-debug        # also build the foss debug APK
#   scripts/build-apks.sh --flavor foss       # build one flavor's release APK
#   scripts/build-apks.sh --with-tests        # run unit tests first, abort on failure
#   scripts/build-apks.sh --upload            # also attach the APKs to a GitHub release
#   scripts/build-apks.sh --upload-only ...   # skip the build, upload existing dist/ APKs
#
# Exit codes: 0 success, 1 preflight failure, 2 build failure, 3 upload failure.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WITH_TESTS=0
WITH_DEBUG=0
DO_UPLOAD=0
SKIP_BUILD=0
FLAVOR="all"
while [ $# -gt 0 ]; do
  case "$1" in
    --with-tests) WITH_TESTS=1 ;;
    --with-debug) WITH_DEBUG=1 ;;
    --upload)     DO_UPLOAD=1 ;;
    --upload-only) DO_UPLOAD=1; SKIP_BUILD=1 ;;
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
# Gradle auto-provisioned toolchains (~/.gradle/jdks) — where a JDK 21 landed
# on machines that never installed one manually.
for jdk_dir in "$HOME"/.gradle/jdks/*/; do
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
# 3. Signing preflight: release builds without credentials would produce
#    unsigned APKs that Android refuses to install. Abort instead.
# ---------------------------------------------------------------------------
lp_val() { sed -n "s/^$1=//p" "$ROOT/local.properties" 2>/dev/null | tr -d '\r' | sed 's/\\:/:/g' | head -1; }
RELEASE_STORE_PASSWORD="${AUDIFY_RELEASE_STORE_PASSWORD:-${STORE_PASSWORD:-$(lp_val AUDIFY_RELEASE_STORE_PASSWORD)}}"
RELEASE_KEY_ALIAS="${AUDIFY_RELEASE_KEY_ALIAS:-${KEY_ALIAS:-$(lp_val AUDIFY_RELEASE_KEY_ALIAS)}}"
RELEASE_KEY_PASSWORD="${AUDIFY_RELEASE_KEY_PASSWORD:-${KEY_PASSWORD:-$(lp_val AUDIFY_RELEASE_KEY_PASSWORD)}}"
RELEASE_STORE_FILE="$(lp_val AUDIFY_RELEASE_STORE_FILE)"
RELEASE_STORE_FILE="${RELEASE_STORE_FILE:-keystore/release.keystore}"
# Mirror Gradle's project-relative file() resolution: the path is relative to
# app/, or absolute as-is.
case "$RELEASE_STORE_FILE" in
  /*|[A-Za-z]:*) RELEASE_STORE_PATH="$RELEASE_STORE_FILE" ;;
  *)             RELEASE_STORE_PATH="$ROOT/app/$RELEASE_STORE_FILE" ;;
esac

BUILDING_RELEASE=0
case "$FLAVOR" in
  all|foss|gms|izzy) BUILDING_RELEASE=1 ;;
esac
if [ "$BUILDING_RELEASE" -eq 1 ] && [ "$SKIP_BUILD" -eq 0 ]; then
  if [ -z "$RELEASE_STORE_PASSWORD" ] || [ -z "$RELEASE_KEY_PASSWORD" ]; then
    fail "No release signing credentials (set AUDIFY_RELEASE_STORE_PASSWORD/AUDIFY_RELEASE_KEY_PASSWORD in local.properties, or STORE_PASSWORD/KEY_PASSWORD in the environment). Release APKs would be unsigned and uninstallable."
  fi
  [ -f "$RELEASE_STORE_PATH" ] || fail "Release keystore not found at $RELEASE_STORE_PATH (see local.properties AUDIFY_RELEASE_STORE_FILE)."
  log "Release signing: $RELEASE_STORE_FILE (alias ${RELEASE_KEY_ALIAS:-default})"
fi

# ---------------------------------------------------------------------------
# 4/5. Build (with optional test gate; skipped entirely by --upload-only).
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

if [ "$SKIP_BUILD" -eq 1 ]; then
  log "--upload-only: skipping tests and build, using existing Gradle outputs."
else
  if [ "$WITH_TESTS" -eq 1 ]; then
    log "Running unit tests (foss debug)..."
    ./gradlew --console=plain -q :app:testFossDebugUnitTest \
      || fail "Unit tests failed — aborting (run without --with-tests to skip)."
    log "All tests passed."
  fi

  log "Building: ${TASKS[*]}"
  ./gradlew --console=plain "${TASKS[@]}" \
    || fail "Gradle build failed (exit $?)."
fi

# ---------------------------------------------------------------------------
# 6. Collect outputs with unambiguous names.
# ---------------------------------------------------------------------------
VERSION=$(grep -E '^\s*versionName = "' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
[ -n "$VERSION" ] || fail "Could not read versionName from app/build.gradle.kts"

# Signature gate: an unsigned or corrupt APK must never reach dist/ or a
# release page. apksigner runs from the build-tools via its standalone jar,
# so this works identically on Windows Git Bash and CI.
SDK_DIR="${ANDROID_HOME:-$(lp_val sdk.dir)}"
APKSIGNER_JAR="$(ls "$SDK_DIR/build-tools/"*/lib/apksigner.jar 2>/dev/null | sort -V | tail -1 || true)"
verify_signed() { # verify_signed <apk>
  if [ -z "$APKSIGNER_JAR" ]; then
    warn "apksigner.jar not found under $SDK_DIR/build-tools — skipping signature verification of $(basename "$1")."
    return 0
  fi
  if ! "$JAVA_HOME/bin/java" -jar "$APKSIGNER_JAR" verify "$1" >/dev/null 2>&1; then
    fail "Signature verification FAILED for $(basename "$1") — unsigned or corrupt. Nothing was shipped; fix signing and rebuild."
  fi
  log "  ✓ signature OK: $(basename "$1")"
}
SHORT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "nogit")
DIST="$ROOT/dist/${VERSION}-${SHORT_SHA}"
mkdir -p "$DIST"

copy_apk() { # copy_apk <glob> <final-name>
  local found=""
  for f in $1; do
    [ -f "$f" ] || continue
    verify_signed "$f"
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

# ---------------------------------------------------------------------------
# 7. Optional upload: attach the APKs to a GitHub prerelease v<version>+<sha>.
#    Credentials come from the git credential manager (the same store git
#    push uses), so there is no token hardcoded anywhere.
# ---------------------------------------------------------------------------
if [ "$DO_UPLOAD" -eq 1 ]; then
  [ "$SHORT_SHA" != "nogit" ] || fail "Upload needs a git repo to name the release tag."

  # Repo slug from the origin URL (https or ssh); fall back to the known repo.
  REPO=$(git remote get-url origin 2>/dev/null | sed -E 's#^.*github\.com[:/]##; s#\.git$##' || true)
  [ -n "$REPO" ] || REPO="sachit1751-art/Audify"

  creds=$(printf 'protocol=https\nhost=github.com\n\n' \
    | GIT_TERMINAL_PROMPT=0 GCM_INTERACTIVE=never git credential fill 2>/dev/null || true)
  GH_TOKEN=$(printf '%s' "$creds" | grep '^password=' | cut -d= -f2- || true)
  [ -n "$GH_TOKEN" ] || fail "No GitHub credentials in the git credential manager; run a manual git push once to store them."

  api() { curl -s -H "Authorization: token $GH_TOKEN" -H "Accept: application/vnd.github+json" "$@"; }

  TAG="v${VERSION}+${SHORT_SHA}"
  log "Uploading to release $TAG (repo $REPO)..."

  # Reuse the release for this commit if it exists, else create it.
  rel_json="$(api "https://api.github.com/repos/$REPO/releases/tags/$TAG")"
  if printf '%s' "$rel_json" | grep -q '"tag_name" *:'; then
    RELEASE_ID=$(printf '%s' "$rel_json" | grep -m1 -Eo '"id" *: *[0-9]+' | grep -o '[0-9]*')
    log "  reusing existing release (id $RELEASE_ID)"
  else
    FILE_LIST=$(for f in "$DIST"/*.apk; do printf '%s\n' "- $(basename "$f")"; done)
    FILE_LIST_JSON=${FILE_LIST//$'\n'/\\n}
    create_body=$(cat <<EOF
{
  "tag_name": "$TAG",
  "target_commitish": "$(git rev-parse HEAD)",
  "name": "Audify $TAG",
  "draft": false,
  "prerelease": true,
  "body": "Local build from commit $SHORT_SHA, produced by scripts/build-apks.sh. Release APKs are signed with the project release keystore; debug builds are debug-signed and install side-by-side with release versions.\\n\\nFiles:\\n$FILE_LIST_JSON"
}
EOF
)
    create_resp="$(api -X POST -H "Content-Type: application/json" \
      -d "$create_body" "https://api.github.com/repos/$REPO/releases")"
    RELEASE_ID=$(printf '%s' "$create_resp" | grep -m1 -Eo '"id" *: *[0-9]+' | grep -o '[0-9]*')
    [ -n "$RELEASE_ID" ] || {
      warn "Could not create the release. API said:"
      printf '%s\n' "$create_resp" | head -5 >&2
      exit 3
    }
    log "  created release $TAG (id $RELEASE_ID)"
  fi

  assets_json="$(api "https://api.github.com/repos/$REPO/releases/$RELEASE_ID/assets?per_page=100")"

  asset_id() { # <assets-json> <exact asset name> -> asset id (pretty JSON has id 2 lines above name)
    grep -B3 "\"name\": \"$2\"," <<<"$1" | grep -m1 -Eo '"id" *: *[0-9]+' | grep -o '[0-9]*' || true
  }
  asset_field() { # <assets-json> <exact asset name> <state|size> -> field value from the first match after the name
    awk -v key="\"name\": \"$2\"," -v f="$3" '
      index($0, key) { seen=1; next }
      seen && match($0, "\"" f "\":") {
        line=substr($0, RSTART)
        sub("\"" f "\": ", "", line); sub(/,.*$/, "", line); gsub(/"/, "", line)
        print line; exit
      }' <<<"$1"
  }

  upload_failures=0
  for f in "$DIST"/*.apk; do
    name=$(basename "$f")
    size=$(stat -c %s "$f")
    old_id=$(asset_id "$assets_json" "$name")
    if [ -n "$old_id" ]; then
      old_state=$(asset_field "$assets_json" "$name" state)
      old_size=$(asset_field "$assets_json" "$name" size)
      if [ "$old_state" = "uploaded" ] && [ "$old_size" = "$size" ]; then
        log "  = $name already on the release (identical), skipping"
        continue
      fi
      # Stale, orphaned ("starter") or changed asset: replace it.
      api -X DELETE -o /dev/null "https://api.github.com/repos/$REPO/releases/assets/$old_id"
      log "  - replaced existing $name (state: ${old_state:-unknown})"
    fi
    log "  ↑ uploading $name ($(du -h "$f" | cut -f1)) — this can take a while..."
    upload_resp="$(curl -s --max-time 3600 --speed-time 120 --speed-limit 1024 \
      -X POST \
      -H "Authorization: token $GH_TOKEN" \
      -H "Content-Type: application/vnd.android.package-archive" \
      --data-binary "@$f" \
      "https://uploads.github.com/repos/$REPO/releases/$RELEASE_ID/assets?name=$name")"
    # uploads.github.com answers with minified JSON while api.github.com is
    # pretty-printed — match both spacing styles.
    if printf '%s' "$upload_resp" | grep -Eq '"state" *: *"uploaded"'; then
      log "  ✓ $name uploaded"
    else
      warn "Upload of $name failed. API said:"
      printf '%s\n' "$upload_resp" | head -5 >&2
      upload_failures=$((upload_failures + 1))
    fi
  done

  if [ "$upload_failures" -gt 0 ]; then
    fail "$upload_failures asset upload(s) failed — see warnings above."
  fi
  log "Release page: https://github.com/$REPO/releases/tags/$(printf '%s' "$TAG" | sed 's/+/%2B/g')"
fi

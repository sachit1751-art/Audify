#!/usr/bin/env bash
# Audify cross-platform environment bootstrap (macOS/Linux).
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { printf 'ERROR: %s\n' "$1" >&2; exit 1; }
command -v java >/dev/null 2>&1 || fail 'JDK 21 is required. Install Eclipse Temurin 21 from https://adoptium.net/temurin/releases/?version=21.'
JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
[[ "$JAVA_VERSION" == *'21.'* ]] || fail "Audify requires JDK 21; detected: $JAVA_VERSION"
printf 'JDK 21: OK\n'

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" ]]; then
  case "$(uname -s)" in
    Darwin) SDK="$HOME/Library/Android/sdk" ;;
    Linux) SDK="${HOME}/Android/Sdk" ;;
    *) fail 'Set ANDROID_HOME to your Android SDK path.' ;;
  esac
fi
[[ -d "$SDK" ]] || fail "Android SDK not found at '$SDK'. Install Android Studio and Android SDK Command-line Tools (latest), then set ANDROID_HOME."
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  SDKMANAGER="$(find "$SDK/cmdline-tools" -type f -name sdkmanager 2>/dev/null | head -n 1 || true)"
fi
if [[ ! -x "$SDKMANAGER" ]]; then SDKMANAGER=""; printf 'sdkmanager unavailable; validating installed SDK packages.\n'; fi

PACKAGES=(platform-tools 'platforms;android-37' 'build-tools;36.0.0' 'ndk;28.2.13676358' 'cmake;3.22.1')
MISSING=()
if [[ -n "$SDKMANAGER" ]]; then
  INSTALLED="$("$SDKMANAGER" --sdk_root="$SDK" --list_installed 2>&1)" || fail 'sdkmanager could not inspect the SDK.'
  for package in "${PACKAGES[@]}"; do grep -Fq "$package" <<<"$INSTALLED" || MISSING+=("$package"); done
else
  for package_path in platform-tools build-tools/36.0.0 ndk/28.2.13676358 cmake/3.22.1; do
    [[ -d "$SDK/$package_path" ]] || MISSING+=("$package_path")
  done
  compgen -G "$SDK/platforms/android-37*" >/dev/null || MISSING+=('platforms;android-37')
fi
if ((${#MISSING[@]})); then
  [[ -n "$SDKMANAGER" ]] || fail "Missing SDK packages (${MISSING[*]}). Install Android SDK Command-line Tools (latest) in Android Studio > SDK Manager > SDK Tools, then rerun."
  printf 'Installing Android SDK packages: %s\n' "${MISSING[*]}"
  yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true
  "$SDKMANAGER" --sdk_root="$SDK" "${MISSING[@]}"
fi

# Update only sdk.dir; retain any existing API keys and project-local settings.
SDK_VALUE="$(cd "$SDK" && pwd -P)"
TMP="$(mktemp)"
if [[ -f local.properties ]]; then awk '!/^[[:space:]]*sdk\.dir=/' local.properties > "$TMP"; fi
printf 'sdk.dir=%s\n' "$SDK_VALUE" >> "$TMP"
mv "$TMP" local.properties
[[ -f gradlew ]] || fail 'Gradle wrapper is missing from this checkout.'
if [[ ! -f keystore.properties ]]; then
  printf 'NOTE: no local release-signing config. Restore your private keystore backup for locally signed production APKs; CI uses GitHub Secrets.\n'
else
  printf 'Local signing config exists; left untouched.\n'
fi
printf 'Audify setup complete. Run: ./gradlew :app:tasks --quiet\n'
#!/usr/bin/env bash
# ==============================================================================
# J.A.R.V.I.S v1.0.1 — Fast One-Click APK Builder (Linux / macOS / WSL)
# Usage:
#   ./build-apk.sh             -> Fast signed Release APK (auto-creates keystore)
#   ./build-apk.sh --debug     -> Instant signed Debug APK (fastest incremental)
#   ./build-apk.sh --optimize  -> Full R8 minified & shrunk Release APK
# ==============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

MODE="${1:---fast-release}"
OUTPUT_APK="$ROOT_DIR/JARVIS-v1.0.1.apk"

if command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
elif [[ -x "$ROOT_DIR/gradlew" ]]; then
  GRADLE_CMD="$ROOT_DIR/gradlew"
else
  echo "[ERROR] Neither 'gradle' nor './gradlew' was found in PATH."
  exit 1
fi

COMMON_FLAGS=("--parallel" "--build-cache" "-x" "lint" "-x" "test")

if [[ "$MODE" == "--debug" ]]; then
  if [[ ! -f "$ROOT_DIR/debug.keystore" ]]; then
    if [[ -f "$ROOT_DIR/debug.keystore.base64" ]]; then
      echo "==> Restoring debug.keystore from debug.keystore.base64..."
      tr -d '[:space:]' < "$ROOT_DIR/debug.keystore.base64" | (base64 -d 2>/dev/null || base64 -D) > "$ROOT_DIR/debug.keystore"
    else
      echo "==> Generating debug.keystore..."
      keytool -genkeypair -noprompt \
        -keystore "$ROOT_DIR/debug.keystore" \
        -alias androiddebugkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Android Debug,O=Android,C=US"
    fi
  fi
  echo "==> Building fast signed Debug APK..."
  "$GRADLE_CMD" :app:fastApk "${COMMON_FLAGS[@]}"
else
  # Ensure a release signing keystore exists so build never fails without manual setup
  export KEYSTORE_PATH="${KEYSTORE_PATH:-$ROOT_DIR/my-upload-key.jks}"
  export STORE_PASSWORD="${STORE_PASSWORD:-jarvis-local-release-key}"
  export KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"

  if [[ ! -f "$KEYSTORE_PATH" ]]; then
    echo "==> Generating local release signing keystore ($KEYSTORE_PATH)..."
    keytool -genkeypair -noprompt \
      -keystore "$KEYSTORE_PATH" \
      -alias upload \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000 \
      -storepass "$STORE_PASSWORD" \
      -keypass "$KEY_PASSWORD" \
      -dname "CN=JARVIS Android, OU=Mobile, O=JARVIS, L=Algiers, ST=DZ, C=DZ"
  fi

  if [[ "$MODE" == "--optimize" ]]; then
    echo "==> Building R8-optimized signed Release APK..."
    "$GRADLE_CMD" :app:assembleRelease "${COMMON_FLAGS[@]}"
  else
    echo "==> Building fast signed Release APK (skipping slow R8 pass)..."
    "$GRADLE_CMD" :app:assembleRelease -PfastApk=true "${COMMON_FLAGS[@]}"
  fi

  if [[ -f "$ROOT_DIR/app/build/outputs/apk/release/app-release.apk" ]]; then
    BUILT_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
  else
    BUILT_APK="$(find "$ROOT_DIR/app/build/outputs/apk/release" -maxdepth 1 -name '*.apk' | head -n 1)"
  fi
  if [[ -z "$BUILT_APK" || ! -f "$BUILT_APK" ]]; then
    echo "[ERROR] Release APK was not found in app/build/outputs/apk/release/"
    exit 1
  fi
  cp "$BUILT_APK" "$OUTPUT_APK"
fi

if [[ -f "$OUTPUT_APK" ]]; then
  SIZE="$(du -h "$OUTPUT_APK" | cut -f1)"
  echo ""
  echo "============================================================"
  echo " Ready-to-Install Signed APK Created Successfully!"
  echo " File : $OUTPUT_APK"
  echo " Size : $SIZE"
  if command -v sha256sum >/dev/null 2>&1; then
    echo " SHA256: $(sha256sum "$OUTPUT_APK" | awk '{print $1}')"
  fi
  echo "============================================================"
fi

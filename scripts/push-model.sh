#!/usr/bin/env bash
#
# scripts/push-model.sh — downloads (if needed) and pushes the on-device Gemma model to a
# connected Android device's app-specific external storage, where OnDeviceEngine expects it
# (getExternalFilesDir("models")/gemma-4-E2B-it.litertlm).
#
# Usage:
#   ./scripts/push-model.sh
#
# Env overrides:
#   CACHE_DIR   — where the downloaded model is cached locally (default: scripts/models/)
#   ANDROID_HOME / ANDROID_SDK_ROOT — searched for platform-tools/adb if adb isn't on PATH
#   ANDROID_SERIAL — picks a specific device when more than one is connected
#
# Requirements: curl, sha256sum (or shasum), adb.
#
# What it does:
#   1. Downloads MODEL_FILE from HF_URL into CACHE_DIR if not already cached there (resumable
#      via curl -C -, since the file is ~2.5GB).
#   2. Verifies the freshly downloaded file's SHA256 against EXPECTED_SHA256; deletes the file
#      and aborts on mismatch. (Skipped on a cache hit — nothing was just downloaded to check.)
#   3. Confirms a device is connected via `adb devices`.
#   4. Pushes the cached file to the app's external files/models/ dir (not via run-as/internal
#      storage — OnDeviceEngine reads getExternalFilesDir(), which resolves there).
#   5. Compares the pushed file's on-device size (adb shell ls -l) against the local file size.

set -euo pipefail

# ---- Configuration -------------------------------------------------------------------------
MODEL_FILE="gemma-4-E2B-it.litertlm"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CACHE_DIR="${CACHE_DIR:-$SCRIPT_DIR/models}"
HF_URL="https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
# Verified 2026-07-03 by fetching the model's Git LFS pointer file directly —
# https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/raw/main/gemma-4-E2B-it.litertlm
# returns "oid sha256:<hash> / size 2588147712" — and cross-checked against a local copy's own
# sha256sum, which matched exactly.
EXPECTED_SHA256="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
PACKAGE_NAME="com.example.storymind"
DEVICE_MODEL_DIR="/sdcard/Android/data/${PACKAGE_NAME}/files/models"
# ----------------------------------------------------------------------------------------------

log() { echo "[push-model] $*"; }
die() { echo "[push-model] ERROR: $*" >&2; exit 1; }

find_adb() {
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return
    fi
    local candidates=(
        "${ANDROID_HOME:-}/platform-tools/adb"
        "${ANDROID_SDK_ROOT:-}/platform-tools/adb"
        "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
        "${HOME:-}/Library/Android/sdk/platform-tools/adb"
        "${HOME:-}/Android/Sdk/platform-tools/adb"
    )
    local c
    for c in "${candidates[@]}"; do
        [ -n "$c" ] && [ -x "$c" ] && { echo "$c"; return; }
    done
    die "adb not found on PATH or in common Android SDK locations. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        die "Neither sha256sum nor shasum is available to verify the download."
    fi
}

file_size_of() {
    stat -c%s "$1" 2>/dev/null || stat -f%z "$1" 2>/dev/null || die "Could not stat $1"
}

command -v curl >/dev/null 2>&1 || die "curl is required."
ADB="$(find_adb)"
log "Using adb: $ADB"

LOCAL_PATH="$CACHE_DIR/$MODEL_FILE"
mkdir -p "$CACHE_DIR"

# 1. Download if not cached -------------------------------------------------------------------
if [ -f "$LOCAL_PATH" ]; then
    log "Using cached model at $LOCAL_PATH (delete it to force a re-download)."
else
    log "Downloading $MODEL_FILE from $HF_URL (~2.5GB, resumable if interrupted)..."
    curl -L -C - --fail -o "$LOCAL_PATH" "$HF_URL" || die "Download failed."

    # 2. Verify checksum, only right after a fresh download ------------------------------------
    log "Verifying SHA256..."
    ACTUAL_SHA256="$(sha256_of "$LOCAL_PATH")"
    if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
        rm -f "$LOCAL_PATH"
        die "SHA256 mismatch (expected $EXPECTED_SHA256, got $ACTUAL_SHA256). Deleted the corrupt download."
    fi
    log "SHA256 verified."
fi

# 3. Confirm a device is connected -------------------------------------------------------------
DEVICE_COUNT="$("$ADB" devices | awk 'NR>1 && $2=="device"{c++} END{print c+0}')"
[ "$DEVICE_COUNT" -ge 1 ] || die "No authorized device found. Run '$ADB devices' and check USB debugging."
[ "$DEVICE_COUNT" -eq 1 ] || log "Warning: multiple devices connected; set ANDROID_SERIAL to pick one."

# 4. Push to the app's external files/models dir ------------------------------------------------
log "Creating $DEVICE_MODEL_DIR on device..."
"$ADB" shell "mkdir -p '$DEVICE_MODEL_DIR'"

log "Pushing $MODEL_FILE to device (this can take several minutes)..."
# The doubled leading slash below defeats Git-Bash/MSYS's automatic POSIX->Windows path
# conversion, which otherwise silently rewrites "/sdcard/..." into a bogus local Windows path
# before adb ever sees it (adb push then fails with "remote secure_mkdirs() failed"). A real
# device shell collapses "//sdcard/..." to the same path as "/sdcard/...", so this is a no-op
# on native Linux/macOS bash.
"$ADB" push "$LOCAL_PATH" "/$DEVICE_MODEL_DIR/$MODEL_FILE" || die "adb push failed."

# 5. Verify pushed size matches local size -------------------------------------------------------
LOCAL_SIZE="$(file_size_of "$LOCAL_PATH")"
REMOTE_LS="$("$ADB" shell "ls -l '$DEVICE_MODEL_DIR/$MODEL_FILE'")"
log "Device file: $REMOTE_LS"
REMOTE_SIZE="$(echo "$REMOTE_LS" | tr -s ' ' | cut -d' ' -f5)"

[ "$LOCAL_SIZE" = "$REMOTE_SIZE" ] || die "Size mismatch: local=$LOCAL_SIZE remote=$REMOTE_SIZE. Push may be incomplete — re-run this script."

log "Done. $MODEL_FILE ($LOCAL_SIZE bytes) is on the device at $DEVICE_MODEL_DIR/$MODEL_FILE."

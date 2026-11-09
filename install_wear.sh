#!/usr/bin/env bash
# installs or updates a supplied watch apk
set -euo pipefail

usage() {
  echo "Usage: $0 WATCH_APK"
  echo "Example: $0 wear-release.apk (signed watch APK from the matching Codeberg release)"
  echo "Install/update on exactly one authorized watch, or select it with WATCH_SERIAL."
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 1 || ! -f "$1" ]]; then
  usage >&2
  echo 'ERROR: Supply the path to an existing Wear OS APK.' >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo 'ERROR: Install Android SDK platform-tools and add adb to PATH.' >&2
  exit 1
fi

WATCH_SERIAL="${WATCH_SERIAL:-}"
if [[ -z "$WATCH_SERIAL" ]]; then
  watches=()

  while read -r serial state rest; do
    [[ "$state" == "device" ]] || continue
    characteristics=$(adb -s "$serial" shell getprop ro.build.characteristics </dev/null | tr -d '\r')
    [[ "$characteristics" != *watch* ]] || watches+=("$serial")
  done < <(adb devices)

  if [[ ${#watches[@]} -ne 1 ]]; then
    echo 'ERROR: Connect exactly one authorized watch or set WATCH_SERIAL.' >&2
    exit 1
  fi

  WATCH_SERIAL="${watches[0]}"
fi

if [[ $(adb -s "$WATCH_SERIAL" get-state 2>/dev/null) != device ]]; then
  echo 'ERROR: The selected watch is not connected and authorized. Check adb devices.' >&2
  exit 1
fi

if [[ $(adb -s "$WATCH_SERIAL" shell getprop ro.build.characteristics </dev/null | tr -d '\r') != *watch* ]]; then
  echo 'ERROR: WATCH_SERIAL does not identify a watch. Nothing was installed.' >&2
  exit 1
fi

echo "==> Watch: $WATCH_SERIAL"
if ! adb -s "$WATCH_SERIAL" install -r "$1"; then
  echo 'ERROR: Installation failed. No app or data was removed. Check the APK and signing certificate.' >&2
  exit 1
fi
adb -s "$WATCH_SERIAL" shell am start -n de.theunknown.yuntis/sh.lsx.untisx.wear.MainActivity

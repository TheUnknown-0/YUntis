#!/bin/bash
# builds a signed release apk and installs it on a connected phone via adb
set -euo pipefail

cd "$(dirname "$0")"

KEYSTORE_FILE="untisx.keystore"
if [[ -f "$HOME/untisx.keystore" ]]; then
  cp "$HOME/untisx.keystore" "$KEYSTORE_FILE"
  trap 'rm -f "$KEYSTORE_FILE"' EXIT
else
  echo "ERROR: $HOME/untisx.keystore not found." >&2
  exit 1
fi

KEYSTORE_PASSWORD=$(grep -m1 '^KEYSTORE_PASSWORD=' .env | cut -d'=' -f2)
if [[ -z "$KEYSTORE_PASSWORD" ]]; then
  echo "ERROR: KEYSTORE_PASSWORD not found in .env" >&2
  exit 1
fi

echo '# Building apk...'
./gradlew :app:assembleRelease -PkeystorePassword="$KEYSTORE_PASSWORD" --console=plain 2>&1 | grep -E "^e:|BUILD" | tail -1

echo '# Installing apk...'
adb install -r app/build/outputs/apk/release/app-release.apk 2>&1 | tail -1

echo -e '\nDone.'

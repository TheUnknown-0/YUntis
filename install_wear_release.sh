#!/bin/bash
# builds a signed watch apk and installs it on a connected watch via adb
set -euo pipefail

cd "$(dirname "$0")"

SKIP_TESTS=false
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=true ;;
    *) echo "ERROR: Unknown argument: $arg (supported: --skip-tests)" >&2; exit 1 ;;
  esac
done

if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in /usr/lib/jvm/java-25-openjdk /usr/lib/jvm/java-21-openjdk "$HOME"/.jdks/* /usr/lib/jvm/*; do
    if [[ -x "$candidate/bin/java" ]]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
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

if [[ $(adb -s "$WATCH_SERIAL" shell getprop ro.build.characteristics | tr -d '\r') != *watch* ]]; then
  echo 'ERROR: WATCH_SERIAL does not identify an authorized watch.' >&2
  exit 1
fi

echo "==> Watch: $WATCH_SERIAL"

if [[ "$SKIP_TESTS" != true ]]; then
  ./gradlew :wear:testDebugUnitTest :core:testDebugUnitTest --console=plain
fi

KEYSTORE_FILE="untisx.keystore"
if [[ -f "$KEYSTORE_FILE" ]]; then
  :
elif [[ -f "$HOME/untisx.keystore" ]]; then
  cp "$HOME/untisx.keystore" "$KEYSTORE_FILE"
  trap 'rm -f "$KEYSTORE_FILE"' EXIT
else
  echo "ERROR: $HOME/untisx.keystore not found." >&2
  exit 1
fi

KEYSTORE_PASSWORD=$(grep -m1 '^KEYSTORE_PASSWORD=' .env 2>/dev/null | cut -d'=' -f2- | tr -d '\r' || true)
if [[ -z "$KEYSTORE_PASSWORD" ]]; then
  echo 'ERROR: KEYSTORE_PASSWORD not found in .env' >&2
  exit 1
fi

export ORG_GRADLE_PROJECT_keystorePassword="$KEYSTORE_PASSWORD"

./gradlew :wear:assembleRelease --console=plain
WATCH_SERIAL="$WATCH_SERIAL" ./install_wear.sh wear/build/outputs/apk/release/wear-release.apk

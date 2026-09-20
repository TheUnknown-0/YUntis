#!/bin/bash
# builds a signed release apk and installs it on a connected phone via adb
set -euo pipefail

cd "$(dirname "$0")"

find_java_home() {
  local candidate

  for candidate in \
    "${JAVA_HOME:-}" \
    /usr/lib/jvm/java-21-openjdk \
    /tmp/opencode/jdk21 \
    "$HOME/.jdks/temurin-21" \
    "$HOME"/.jdks/ms-21* \
    "$HOME"/.jdks/jbr-21* \
    "$HOME"/.jdks/*21* \
    /usr/lib/jvm/*21* \
    "$HOME"/.jdks/* \
    /usr/lib/jvm/*; do

    if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

if JAVA_CANDIDATE=$(find_java_home); then
  export JAVA_HOME="$JAVA_CANDIDATE"
  echo "==> Using JDK: $JAVA_HOME"
else
  echo "WARNING: No JDK detected; Gradle build may fail." >&2
fi

KEYSTORE_FILE="untisx.keystore"
if [[ -f "$HOME/untisx.keystore" ]]; then
  cp "$HOME/untisx.keystore" "$KEYSTORE_FILE"
  trap 'rm -f "$KEYSTORE_FILE"' EXIT
else
  echo "ERROR: $HOME/untisx.keystore not found." >&2
  exit 1
fi

KEYSTORE_PASSWORD=$(grep -m1 '^KEYSTORE_PASSWORD=' .env 2>/dev/null | cut -d'=' -f2 || true)
if [[ -z "$KEYSTORE_PASSWORD" ]]; then
  echo "ERROR: KEYSTORE_PASSWORD not found in .env" >&2
  exit 1
fi

echo '# Building apk...'
./gradlew :app:assembleRelease -PkeystorePassword="$KEYSTORE_PASSWORD" --console=plain 2>&1 | grep -E "^e:|BUILD" | tail -1

echo '# Installing apk...'
adb install -r app/build/outputs/apk/release/app-release.apk 2>&1 | tail -1

echo -e '\nDone.'

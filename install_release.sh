#!/bin/bash
# builds a signed release apk and installs it on a connected phone via adb
set -euo pipefail

cd "$(dirname "$0")"

SKIP_TESTS=false
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=true ;;
    *)
      echo "ERROR: Unknown argument: $arg (supported: --skip-tests)" >&2
      exit 1
      ;;
  esac
done

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

if [[ "$SKIP_TESTS" == true ]]; then
  echo '==> Skipping unit tests (--skip-tests).'
else
  echo '==> Running unit tests...'
  
  set +e
  ./gradlew :app:testDebugUnitTest --console=plain 2>&1 | tail -n 25
  tests_status=${PIPESTATUS[0]}
  set -e

  if [[ $tests_status -ne 0 ]]; then
    echo 'ERROR: Unit tests failed - aborting release build.' >&2
    echo '       Report: app/build/reports/tests/testDebugUnitTest/index.html' >&2
    exit 1
  fi
fi

KEYSTORE_FILE="untisx.keystore"
if [[ -f "$HOME/untisx.keystore" ]]; then
  cp "$HOME/untisx.keystore" "$KEYSTORE_FILE"
  trap 'rm -f "$KEYSTORE_FILE"' EXIT
else
  echo "ERROR: $HOME/untisx.keystore not found." >&2
  exit 1
fi

KEYSTORE_PASSWORD=$(grep -m1 '^KEYSTORE_PASSWORD=' .env 2>/dev/null | cut -d'=' -f2- | tr -d '\r' || true)
if [[ -z "$KEYSTORE_PASSWORD" ]]; then
  echo "ERROR: KEYSTORE_PASSWORD not found in .env" >&2
  exit 1
fi

export ORG_GRADLE_PROJECT_keystorePassword="$KEYSTORE_PASSWORD"

echo '# Building apk...'

set +e
./gradlew :app:assembleRelease --console=plain 2>&1 | grep -E "^e:|BUILD" | tail -n 3
build_status=${PIPESTATUS[0]}
set -e

if [[ $build_status -ne 0 ]]; then
  echo 'ERROR: Release build failed.' >&2
  exit 1
fi

echo '# Installing apk...'

set +e
adb install -r app/build/outputs/apk/release/app-release.apk 2>&1 | tail -n 1
install_status=${PIPESTATUS[0]}
set -e

if [[ $install_status -ne 0 ]]; then
  echo 'ERROR: adb install failed.' >&2
  exit 1
fi

echo -e '\nDone.'

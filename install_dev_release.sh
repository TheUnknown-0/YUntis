#!/bin/bash
# a simple script to install the release apk on a phone via adb

echo '# Building apk...'
./gradlew :app:assembleRelease --console=plain 2>&1 | grep -E "^e:|BUILD" | tail -1

echo '# Installing apk...'
adb install -r app/build/outputs/apk/release/app-release.apk 2>&1 | tail -1

echo -e '\nDone.'

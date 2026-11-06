#!/usr/bin/env bash
# Builds, signs and publishes the three released editions as one GitHub release.
#   ./scripts/release.sh <notes-file>
# Bump versionCode and versionName in app/build.gradle.kts and commit first.
# Assets: WordForWord.apk (regular), WordForWordKeypad.apk (keypad), both installing their own
# updates, and WordForWordLite.apk (regular, updates open in the browser, no Play Protect block).
# Each edition's updater downloads only its own file, so keep these names.
set -euo pipefail
cd "$(dirname "$0")/.."
NOTES=${1:?usage: release.sh <notes-file>}

export JAVA_HOME="${JAVA_HOME:-$PWD/.dev/jdk-17.0.20.1+1}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT=$(ls -d "$SDK/build-tools/"* | tail -1)
# Releases so far are signed with the debug key; a different key can't install over them.
KS="$HOME/.android/debug.keystore"

./gradlew testRegularSelfInstallDebugUnitTest testKeypadBrowserDebugUnitTest \
  assembleRegularSelfInstallRelease assembleKeypadSelfInstallRelease assembleRegularBrowserRelease -q

sign() { # <variant folder> <output>
  local tmp; tmp=$(mktemp)
  "$BT/zipalign" -f -p 4 app/build/outputs/apk/$1/release/app-*-release-unsigned.apk "$tmp"
  "$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$2" "$tmp" 2>/dev/null     || "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$2" "$tmp"
  rm -f "$tmp" "$2.idsig"
}
sign regularSelfInstall WordForWord.apk
sign keypadSelfInstall WordForWordKeypad.apk
sign regularBrowser WordForWordLite.apk

VERSION=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | cut -d'"' -f2)
gh release create "v$VERSION" WordForWord.apk WordForWordKeypad.apk WordForWordLite.apk \
  -R isaacweber613/whisper-verbatim --title "Word for Word $VERSION" --notes-file "$NOTES" --target main

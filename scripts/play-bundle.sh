#!/usr/bin/env bash
# Builds the Google Play app bundle: WordForWordPlay.aab, signed with the private upload key.
#   ./scripts/play-bundle.sh
# Upload it in Play Console. It isn't published on GitHub.
# Needs keystore.properties (never committed) pointing at the upload key; see AGENTS.md.
set -euo pipefail
cd "$(dirname "$0")/.."
test -f keystore.properties || { echo "keystore.properties is missing; see AGENTS.md" >&2; exit 1; }
export JAVA_HOME="${JAVA_HOME:-$PWD/.dev/jdk-17.0.20.1+1}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew testPlayDebugUnitTest bundlePlayRelease -q
cp app/build/outputs/bundle/playRelease/app-play-release.aab WordForWordPlay.aab
jarsigner -verify WordForWordPlay.aab >/dev/null && echo "Built and signed WordForWordPlay.aab"

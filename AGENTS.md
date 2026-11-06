# Word for Word: notes for coding agents

## Two editions, always released together

The app ships in two editions built from the same code (product flavors in `app/build.gradle.kts`):

- **regular** (`WordForWord.apk`, "Word for Word"): no keyboard shortcut, Android 11+.
  Its code stays uncompressed in the APK, so the app doesn't grow after installing.
- **keypad** (`WordForWordKeypad.apk`, "Word for Word Keypad"): adds the keyboard shortcut for
  phones with physical keys, Android 5.1+. It asks Android to watch key presses, so Play
  Protect warns "Harmful app blocked" when it is sideloaded; "Install anyway" works.

Isaac (2026-11-06): every change goes into both editions, and every release ships both.
Keypad-only code checks `BuildConfig.KEYPAD`; keypad-only resources live in `app/src/keypad/`.

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit and push.
2. Write release notes to a file, then run `./scripts/release.sh <notes-file>`. It runs the unit
   tests, builds and signs both editions, and creates the GitHub release with both APKs.
   Older releases stay up.
3. Send the APKs to Isaac's phone with `blip-send`.

Each edition's in-app updater downloads only its own APK name from the latest release.

## Never let the app install APKs itself

Isaac (2026-11-06): 0.6.7 to 0.7.0 asked for `REQUEST_INSTALL_PACKAGES` so the app could install
its own updates. Google Play Protect then blocked every one of them as a "Harmful app"; 0.6.6 and
a 0.7.0 test build without that permission installed cleanly on his phone. An accessibility app
that can install other apps looks like malware. The Update row opens the APK in the browser
instead; keep it that way.

## Testing the keyboard shortcut on an emulator

`adb shell input keyevent` and `input keycombination` never reach accessibility services, so
they can't trigger the shortcut. Send real hardware key events with `sendevent` to the
emulator's keyboard device (`getevent -p` lists it; needs an AVD with `hw.keyboard=yes`).
Also, a UI-automation snapshot tool (such as agent-device `snapshot`) pauses accessibility
services while connected; close its session before testing.

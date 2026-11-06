# Word for Word: notes for coding agents

## Editions, always released together

The app is built from one codebase with two flavor choices (`app/build.gradle.kts`):

- edition `regular`: no keyboard shortcut, Android 11+, code stays uncompressed so the app doesn't
  grow after installing. Edition `keypad` ("Word for Word Keypad"): adds the keyboard shortcut for
  phones with physical keys, Android 5.1+.
- updates `selfInstall`: the app downloads and installs its own updates (`src/selfInstall/`).
  Updates `browser`: Update opens the APK in the browser (`src/browser/`), no install permission.

Released APKs: `WordForWord.apk` (regular + selfInstall), `WordForWordKeypad.apk`
(keypad + selfInstall), `WordForWordLite.apk` (regular + browser). The keypad + browser build is
not released yet.

Isaac (2026-11-06): every change goes into every edition, and every release ships all of them.
Keypad-only code checks `BuildConfig.KEYPAD`; keypad-only resources live in `app/src/keypad/`.

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit and push.
2. Write release notes to a file, then run `./scripts/release.sh <notes-file>`. It runs the unit
   tests, builds and signs the released editions, and creates the GitHub release with their APKs.
   Older releases stay up.
3. Send the APKs to Isaac's phone with `blip-send`.

Each edition's in-app updater downloads only its own APK name from the latest release.

## Play Protect and the in-app installer

Isaac (2026-11-06): 0.6.7 to 0.7.0 asked for `REQUEST_INSTALL_PACKAGES` so the app could install
its own updates, and Google Play Protect blocked every one of them as a "Harmful app". 0.6.6 and a
0.7.0 test build without that permission installed cleanly on his phone. An accessibility app that
can install other apps, signed with the public debug key, looks like malware. Isaac still wants
the in-app installer, so it lives only in the `selfInstall` editions; the Lite edition is the one
without the warning.

## Testing the keyboard shortcut on an emulator

`adb shell input keyevent` and `input keycombination` never reach accessibility services, so
they can't trigger the shortcut. Send real hardware key events with `sendevent` to the
emulator's keyboard device (`getevent -p` lists it; needs an AVD with `hw.keyboard=yes`).
Also, a UI-automation snapshot tool (such as agent-device `snapshot`) pauses accessibility
services while connected; close its session before testing.

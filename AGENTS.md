# Word for Word: notes for coding agents

## Two builds, always released together

Every build has the keyboard shortcut and runs on Android 5.1+. Isaac (2026-11-06) chose this
over separate keypad and Android 11+ editions: the shortcut alone doesn't trigger Play Protect,
and about 1 MB more storage after installing is fine. The builds (flavors in
`app/build.gradle.kts`) differ only in how updates install:

- `browser` (`WordForWord.apk`): Update opens the new APK in the browser (`src/browser/`).
- `selfInstall` (`WordForWordSelfUpdate.apk`): the app downloads and installs its own updates
  (`src/selfInstall/`). Play Protect blocks it as harmful; "Install anyway" works.

Every change goes into both builds, and every release ships both.

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit and push.
2. Write release notes to a file, then run `./scripts/release.sh <notes-file>`. It runs the unit
   tests, builds and signs both builds, and creates the GitHub release with both APKs.
   Older releases stay up.
3. Send the APKs to Isaac's phone with `blip-send`.

Each build's updater looks only for its own APK name in the latest release.

## Play Protect and the in-app installer

Isaac (2026-11-06): 0.6.7 to 0.7.0 asked for `REQUEST_INSTALL_PACKAGES` so the app could install
its own updates, and Google Play Protect blocked every one of them as a "Harmful app". 0.6.6 and a
0.7.0 test build without that permission installed cleanly on his phone. An accessibility app that
can install other apps, signed with the public debug key, looks like malware. Isaac still wants
the in-app installer, so it lives only in the `selfInstall` build; `WordForWord.apk` is the one
without the warning. A keypad build without the installer also installed cleanly, so watching
key presses alone doesn't trigger it.

## Google Play build

A third flavor, `play`, is for the Play Store only and isn't released on GitHub (Isaac,
2026-11-06). It has no update check, no Update row and no update notifications
(`BuildConfig.UPDATES` is false), because Play policy bans apps that update themselves outside
Play. Build it with `./scripts/play-bundle.sh`, which writes `WordForWordPlay.aab`, signed with
the private upload key. The key is `~/.android/wordforword-upload.jks`; its password is in
`keystore.properties` in the repo root, which is never committed. Only the Play build uses that
key; the GitHub builds keep the debug key. Every change still goes into all three builds.

## Testing the keyboard shortcut on an emulator

`adb shell input keyevent` and `input keycombination` never reach accessibility services, so
they can't trigger the shortcut. Send real hardware key events with `sendevent` to the
emulator's keyboard device (`getevent -p` lists it; needs an AVD with `hw.keyboard=yes`).
Also, a UI-automation snapshot tool (such as agent-device `snapshot`) pauses accessibility
services while connected; close its session before testing.

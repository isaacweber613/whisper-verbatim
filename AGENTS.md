# Word for Word: notes for coding agents

## Two builds, always released together

Every build has the keyboard shortcut and runs on Android 6+ (Isaac, 2026-11-06: dropped 5.1 so the
PostHog analytics library, which needs Android 6, could be used). Earlier that day he chose this
over separate keypad and Android 11+ editions: the shortcut alone doesn't trigger Play Protect,
and about 1 MB more storage after installing is fine. The builds (flavors in
`app/build.gradle.kts`) differ only in how updates install:

- `browser` (`WordForWord.apk`): Update opens the new APK in the browser (`src/browser/`).
- `selfInstall` (`WordForWordSelfUpdate.apk`): the app downloads and installs its own updates
  (`src/selfInstall/`). Play Protect blocks it as harmful; "Install anyway" works.

Every change goes into both builds, and every release ships both.

## App size

Isaac (2026-11-06): "Anything you do that makes the app bigger in size, you have to consult me
first." Then: "Up to 5% bigger, you don't have to ask me." So measure every change that grows the
APK or its installed size. Up to 5% bigger, go ahead and mention the sizes. More than 5%, tell him
the before and after sizes and wait for his OK.

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit and push.
2. Write release notes to a file, then run `./scripts/release.sh <notes-file>`. It runs the unit
   tests, builds and signs both builds, and creates the GitHub release with both APKs.
   Older releases stay up.
3. Send the APKs to Isaac's phone with `blip-send` only when he asks (Isaac, 2026-11-06).

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
2026-11-06). It has no update check and no Update row
(`BuildConfig.UPDATES` is false), because Play policy bans apps that update themselves outside
Play. Build it with `./scripts/play-bundle.sh`, which writes `WordForWordPlay.aab`, signed with
the private upload key. The key is `~/.android/wordforword-upload.jks`; its password is in
`keystore.properties` in the repo root, which is never committed. Only the Play build uses that
key; the GitHub builds keep the debug key. Every change still goes into all three builds.

## Free minutes server (Play build only)

Isaac (2026-11-06): the Play build gives 30 free minutes a month per phone, so people can dictate
without their own OpenRouter key. With no key entered, the app sends audio to a Cloudflare Worker
(`server/`, deployed as `word-for-word` at https://word-for-word.ynbsd6242.workers.dev), which
holds an OpenRouter key, counts each phone's seconds in the D1 database `word-for-word`, and
answers like OpenRouter. GitHub builds have no server URL and always need the person's own key.

- The OpenRouter key "Word for Word server" has a $10 monthly limit (Isaac's choice) that caps
  total cost, since a faked device ID could get extra free minutes until Play purchases and
  Play Integrity checks are added. It's stored only as the Worker secret `OPENROUTER_KEY`.
- Free minutes per month: `FREE_SECONDS_PER_MONTH` in `server/wrangler.jsonc`.
- Deploy: `cd server && npm test && npx wrangler deploy`.
- Paid plans (Play Billing) aren't built yet; they need Isaac's Play Console account.

## Testing the keyboard shortcut on an emulator

`adb shell input keyevent` and `input keycombination` never reach accessibility services, so
they can't trigger the shortcut. Send real hardware key events with `sendevent` to the
emulator's keyboard device (`getevent -p` lists it; needs an AVD with `hw.keyboard=yes`).
Also, a UI-automation snapshot tool (such as agent-device `snapshot`) pauses accessibility
services while connected; close its session before testing.

## Yiddish takes (Isaac, 2026-11-08)

With Yiddish picked as the language, one recording at a time can go to the Yiddish model running
on Isaac's PC (ivrit.ai yi-whisper-large-v3, served by the `yiddishtry` Cloudflare Worker from the
yiddish-stt repo's `tryout/`): hold the idle mic button (Android's long-press time), or press the
second "Yiddish shortcut keys" shortcut, which shows only with the shortcut on and Yiddish picked.
A quick tap or the normal shortcut sends the take to the normal cloud model with Auto language,
not marked Yiddish. With any other language nothing changes. Code: `YiddishServer.kt`. The PC must
be on; when it's off the Worker answers 503 and the take is kept for a retry.

<p align="center">
  <img src="docs/word-for-word-icon.svg" width="128" height="128" alt="Word for Word logo">
</p>

# Word for Word

Push-to-talk dictation for Android that types **your exact words** into any text box, with no AI rewording.
Keep your normal keyboard: tap the floating mic button (or press a keyboard shortcut), speak, tap again,
and the text appears at the cursor.

Word for Word started as a fork of [kafkasl/phone-whisper](https://github.com/kafkasl/phone-whisper).

## Install

Download one of these from the [latest release](https://github.com/isaacweber613/whisper-verbatim/releases/latest)
and open it on your phone:

- **`WordForWord.apk`** (recommended). When an update is out, the app shows an Update row that downloads
  the new APK in your browser.
- **`WordForWordSelfUpdate.apk`** downloads and installs its own updates. Google Play Protect flags it as
  harmful because it can install apps; tap "Install anyway" if you want it.

Both run on Android 6 and newer. The app walks you through the microphone, the accessibility service
(including Android's "restricted settings" step for apps installed outside the Play Store), and your
[OpenRouter](https://openrouter.ai) API key.

## What it does

- **Cloud transcription through OpenRouter** with your own API key. Built-in models: MAI-Transcribe 2
  (Microsoft, the default), GPT Transcribe (OpenAI), Voxtral Mini Transcribe 3 (Mistral), and Scribe v2
  (ElevenLabs). You can also paste the ID of any speech-to-text model on OpenRouter.
- **About 60 languages**, or Auto to let the model detect it.
- **Verbatim mode** (off by default) asks the model to keep "um", "uh", repeats, and false starts. When it's
  off, models with a cleanup switch are asked to remove them instead.
- **Types straight into the box** at the cursor. If an app doesn't allow that, the text is copied to the
  clipboard and saved in history.
- **The mic button shows only while the keyboard is open**, with bars that move with your voice and a short
  buzz on start and stop (the buzz can be turned off).
- **Keyboard shortcut** for phones with a physical keypad: set any key or key combination and how long to
  press it.
- **Share an audio file to Word for Word** (a voice note, a recording) from any app's share menu. It is
  transcribed, copied to the clipboard and saved in history. A long file is sent in pieces of about 5 minutes.
- **History**: transcriptions are kept for 24 hours, or forever if you turn that on. Tap one to copy it.
- **Clear errors on screen** next to the mic button (no key, wrong key, no internet, out of credit,
  microphone busy, nothing heard). The app checks your key with OpenRouter when you save it.
- **Export and import settings**, including the API key, as a file.
- **Anonymous usage counts** through PostHog, which you can turn off with "Share usage data" in Settings.
- **Yiddish**: with Yiddish picked, holding the mic button (or a second shortcut) sends that recording to a
  Yiddish speech model on the developer's PC. The recording and text are kept for training. If the PC is
  off, it falls back to the normal model.

Word for Word installs as `com.isaacweber.wordforword`, separately from Phone Whisper. Versions before 0.5.0
were called Whisper Verbatim and used a different package; uninstall that once Word for Word is set up.

## Why it needs Accessibility

The accessibility service is used to type the dictated text into the box you're in, and to watch for your
keyboard shortcut if you set one. It does not replace your keyboard and does nothing until you start a
recording.

## Privacy

Audio goes from your phone to OpenRouter, which passes it to the model you picked. There is no Word for Word
server in the GitHub builds. Yiddish takes also go to the developer's PC and are kept
for training. Full policy: [PRIVACY.md](PRIVACY.md).

## Tips

- **Termux**: the terminal area isn't a normal text box. Swipe the extra keys row to switch to Termux's text
  input box and dictate there.

## Building

Needs JDK 17 and the Android SDK.

```bash
./gradlew assembleBrowserDebug        # debug build of WordForWord.apk
./gradlew testBrowserDebugUnitTest    # unit tests
```

There are three build variants, which differ only in how updates work:

| Variant | File | Updates |
|---|---|---|
| `browser` | `WordForWord.apk` | Opens the new APK in the browser |
| `selfInstall` | `WordForWordSelfUpdate.apk` | Downloads and installs itself |
| `play` | `WordForWordPlay.aab` | Through Google Play only; not released on GitHub |

- `scripts/release.sh <notes-file>` runs the tests, builds and signs the two GitHub APKs, and creates the
  GitHub release. Bump `versionCode` and `versionName` in `app/build.gradle.kts` first.
- `scripts/play-bundle.sh` builds the signed Play bundle. It needs the upload key and `keystore.properties`,
  which are not in the repo.
- `server/` is a Cloudflare Worker that gives the Play build 30 free minutes a month per phone. Deploy with
  `cd server && npm test && npx wrangler deploy`.

## License

Apache License 2.0. See [LICENSE](LICENSE).

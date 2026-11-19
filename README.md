<div align="center">
  <img src="docs/word-for-word-icon.svg" width="112" height="112" alt="Word for Word logo">
  <h1>Word for Word</h1>
  <p>Push-to-talk dictation for Android that types <strong>your exact words</strong> into any text box — no AI rewording, and you keep your normal keyboard.</p>
  <p>
    <a href="https://play.google.com/store/apps/details?id=com.isaacweber.wordforword">
      <img src="docs/badge-google-play.svg" height="60" alt="Get it on Google Play">
    </a>
    &nbsp;
    <a href="https://github.com/isaacweber613/whisper-verbatim/releases/latest">
      <img src="docs/badge-github-releases.svg" height="60" alt="Download on GitHub Releases">
    </a>
  </p>
  <p><sub>Android 6+ · Google Play is in testing and opens once approved · GitHub APK uses your own <a href="https://openrouter.ai">OpenRouter</a> API key</sub></p>
</div>

## What it does

- **Types into any app** without replacing your keyboard. Tap the floating mic button (or press a shortcut), speak, tap again, and the text appears at the cursor — or on your clipboard if an app blocks direct typing.
- **Pick your speech model** on OpenRouter — includes MAI-Transcribe 2, GPT Transcribe, Voxtral Mini, and Scribe v2, plus any custom OpenRouter speech-to-text model across ~60 languages.
- **Verbatim mode** (optional) keeps filler words and false starts; leave it off for cleaner transcripts.
- **Transcribe voice notes & videos** — share any audio or video file to Word for Word from another app.
- **Physical keyboard shortcut** — bind any key or key combo to dictate on keypad phones.
- **History** — tap any recent transcription to copy it again.

## Installing from GitHub

Download **`WordForWord.apk`** from the [latest release](https://github.com/isaacweber613/whisper-verbatim/releases/latest) (recommended — opens updates in your browser). There is also **`WordForWordSelfUpdate.apk`**, which installs updates in-app (Play Protect flags it; tap "Install anyway" if you prefer it).

## Privacy & Accessibility

Word for Word uses Android's Accessibility service only to type dictated text into the focused box and watch for your optional keyboard shortcut. Audio goes from your phone to OpenRouter with no Word for Word server in between (optional Yiddish dictation uses a separate server). See [PRIVACY.md](PRIVACY.md) for the full policy.

---

Started as a fork of [kafkasl/phone-whisper](https://github.com/kafkasl/phone-whisper). Licensed under Apache 2.0 ([LICENSE](LICENSE)).

# Privacy policy for Word for Word

Word for Word is an Android dictation app. You tap a floating button, speak, and the app types your exact words into the text box you're in.

## What happens to your recording

When you tap the floating button, the app records audio from the microphone until you tap again. The app sends that recording directly from your phone to OpenRouter, using the OpenRouter API key you entered, to turn it into text. OpenRouter passes the audio to the speech-to-text model you picked in the app. In the Play Store build, if you have not entered a key, the recording goes to Word for Word's server (a Cloudflare Worker), which passes it to OpenRouter and counts your free minutes.

Outside the Yiddish mode below, the app doesn't send your audio or text anywhere else.

OpenRouter and the model provider handle your audio under their own terms and privacy policies.

## Yiddish takes

When Yiddish is selected, holding the mic button or using the Yiddish shortcut sends that recording to a Yiddish speech model on the developer's PC, through a Cloudflare Worker. The recording and the model's text are kept on that PC to improve the Yiddish model. The app sends a random install id and the app version with the take, so takes from one phone can be grouped for training splits; it is not linked to your name. If the PC is off, the recording uses the normal cloud model instead.

## What stays on your phone

- **Your OpenRouter API key**, stored in the app's private storage.
- **Your transcription history**, stored in the app's private storage. By default the app deletes entries after 24 hours. You can choose to keep them, and you can delete them at any time.

Uninstalling the app deletes both.

## Accessibility service

Word for Word uses Android's Accessibility service for two things:

- Showing the floating dictation button while your keyboard is open.
- Typing your dictated words into the text box you're in.

To do this, the app looks at the text box you're in and the text already in it, so it can add your words at the cursor and skip grey placeholder text. After a Yiddish take, it also watches that one text box for your edits, so it can send the corrected words back as the take's correct text. It looks only when you tap into a text box or tap the floating button. The app doesn't save, collect, or share anything else it reads on your screen.

## Usage data

To see how the app is used and where it fails, the app sends anonymous usage counts to [PostHog](https://posthog.com), an analytics service. You can turn this off in Settings with "Share usage data".

What it sends:

- Events: the app opening or closing, a recording starting (by the on-screen button or the keyboard shortcut), a transcription working or failing (with the model name, the recording's length in seconds, and the error code), and whether the text was typed, pasted, or only copied.
- The app version and build, the Android version, and the phone's maker and model.
- A random ID made for this install, so counts can tell installs apart. It isn't linked to your name, account, or phone number.

PostHog also sees the internet address the data comes from, as any web service does.

PostHog never receives your recordings, your words, your transcription history, or your API key.

## Data collection

Word for Word has no accounts, no ads, and no crash reporting. The developer doesn't receive your recordings, your text, or your API key, except for Yiddish takes described above.

## Open source

Word for Word is open source, based on [Phone Whisper](https://github.com/kafkasl/phone-whisper). You can read the code at [github.com/isaacweber613/whisper-verbatim](https://github.com/isaacweber613/whisper-verbatim).

## Contact

For privacy questions, open an issue at [github.com/isaacweber613/whisper-verbatim/issues](https://github.com/isaacweber613/whisper-verbatim/issues).

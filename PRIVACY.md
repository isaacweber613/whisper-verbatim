# Privacy policy for Word for Word

Word for Word is an Android dictation app. You tap a floating button, speak, and the app types your exact words into the text box you're in.

## What happens to your recording

When you tap the floating button, the app records audio from the microphone until you tap again. The app sends that recording directly from your phone to OpenRouter, using the OpenRouter API key you entered, to turn it into text. OpenRouter passes the audio to the speech-to-text model you picked in the app.

The app doesn't send your audio or text anywhere else. There is no Word for Word server.

OpenRouter and the model provider handle your audio under their own terms and privacy policies.

## What stays on your phone

- **Your OpenRouter API key**, stored in the app's private storage.
- **Your transcription history**, stored in the app's private storage. By default the app deletes entries after 24 hours. You can choose to keep them, and you can delete them at any time.

Uninstalling the app deletes both.

## Accessibility service

Word for Word uses Android's Accessibility service for two things:

- Showing the floating dictation button while your keyboard is open.
- Typing your dictated words into the text box you're in.

To do this, the app looks at the text box you're in and the text already in it, so it can add your words at the cursor and skip grey placeholder text. It looks only when you tap into a text box or tap the floating button. The app doesn't save, collect, or share anything it reads on your screen.

## Data collection

Word for Word has no accounts, no analytics, no ads, and no crash reporting. The developer doesn't receive your recordings, your text, or your API key.

## Open source

Word for Word is open source, based on [Phone Whisper](https://github.com/kafkasl/phone-whisper). You can read the code at [github.com/isaacweber613/whisper-verbatim](https://github.com/isaacweber613/whisper-verbatim).

## Contact

For privacy questions, open an issue at [github.com/isaacweber613/whisper-verbatim/issues](https://github.com/isaacweber613/whisper-verbatim/issues).

# Privacy policy for Word for Word

Last updated: November 10, 2026. Developer: Isaac Weber.

Word for Word is an Android dictation app. You tap a floating button, speak, and the app types your exact words into the text box you're in.

## What happens to your recording

After you agree to cloud dictation, tapping the floating button records audio until you tap again. With your own API key, the app sends the recording directly from your phone to OpenRouter. OpenRouter passes the audio to the speech-to-text model you picked. In the Play Store build, if you have not entered a key, the recording goes through Word for Word's server (a Cloudflare Worker), which passes it to OpenRouter. That server processes the recording in transit and does not store the recording or transcription in its database.

For free minutes, the app also sends a SHA-256 hash of its Android device ID. The server stores that hash, the UTC calendar month, and seconds used in a Cloudflare D1 database to enforce the monthly allowance. The hash can stay the same after reinstalling the app. These records currently have no automatic deletion schedule.

Outside the Yiddish mode below, the app doesn't send your audio or text anywhere else.

OpenRouter and the model provider handle your audio under their own terms and privacy policies.

## Yiddish takes

When Yiddish is selected, holding the mic button or using the Yiddish shortcut asks for separate training consent before the first recording. If you agree, that recording goes to a Yiddish speech model on the developer's PC through a Cloudflare Worker. The recording, model text, and your later corrected words are kept on that PC for review and training to improve the Yiddish model. The app sends a random install ID and app version so takes from one installation can be grouped for training splits. The ID is not an account name, but voice and dictated words may themselves identify you. These takes currently have no automatic deletion schedule.

After a Yiddish take, the app follows edits to that take's text box. When the text is sent, the screen changes, another recording starts, or two minutes pass, it sends the corrected words or an outcome such as unchanged or deleted. Text cut to less than half is treated as deleted and is not sent as a correction. Words added to the take's box may be included and are marked for review.

You can turn off future training collection in Settings > Yiddish training. This does not delete takes already sent. That row also lets you copy your recording ID for a deletion request. If the PC is unavailable, the recording uses the normal cloud model instead, through the free-minute server when applicable. A quick tap uses the normal cloud model without Yiddish training.

## What stays on your phone

- **Your OpenRouter API key**, stored in the app's private storage.
- **Your transcription history**, stored in the app's private storage. By default the app deletes entries after 24 hours. You can choose to keep them, and you can delete them at any time.

Uninstalling the app deletes both.

## Accessibility service

Word for Word uses Android's Accessibility service for two things:

- Showing the floating dictation button while your keyboard is open.
- Typing your dictated words into the text box you're in.

To do this, the app looks at the focused text box and the text already in it, so it can add your words at the cursor and skip grey placeholder text. If you agree to Yiddish training, it also watches that take's text box for edits and sends corrected words as described above. If you enable a keyboard shortcut, the service filters key events to detect your selected shortcut. The app does not collect other screen content for analytics. You can disable the service in Android's Accessibility settings.

## Usage data

To see how the app is used and where it fails, the app sends anonymous usage counts to [PostHog](https://posthog.com), an analytics service. You can turn this off in Settings with "Share usage data".

What it sends:

- Events: the app opening or closing, a recording starting (by the on-screen button or the keyboard shortcut), a transcription working or failing (with the model name, the recording's length in seconds, and the error code), and whether the text was typed, pasted, or only copied.
- The app version and build, the Android version, and the phone's maker and model.
- A random ID made for this install, so counts can tell installs apart. It isn't linked to your name, account, or phone number.

PostHog also sees the internet address the data comes from, as any web service does.

PostHog never receives your recordings, your words, your transcription history, or your API key.

## Data collection

Word for Word has no accounts, no ads, and no crash reporting. The developer's services process free-minute recordings in transit and retain Yiddish takes and usage records as described above. Your own OpenRouter API key is not sent to the developer's servers.

Network services, including Cloudflare, OpenRouter, model providers, and PostHog, receive network metadata such as your internet address. Their terms and privacy policies also apply. Analytics retention depends on the hosted PostHog project; this app does not enforce a remote deletion schedule.

## Retention and deletion requests

Deleting history or uninstalling removes local data, but does not delete Yiddish takes, server usage records, or analytics already sent. Turning off Share usage data stops future analytics collection by the app. Turning off Yiddish training stops future training uploads and pending corrections. Neither switch retracts completed transfers or reverses model training already performed.

For a request about remote data, contact the developer through the issue link below and ask for a private contact method before sharing identifiers. For Yiddish takes, copy your recording ID from Settings > Yiddish training before uninstalling. Do not post audio, dictated text, API keys, or identifiers in a public issue. Deletion requests are handled manually; the app has no automatic remote-deletion button. Providers may separately retain data under their own policies.

## Open source

Word for Word is open source, based on [Phone Whisper](https://github.com/kafkasl/phone-whisper). You can read the code at [github.com/isaacweber613/whisper-verbatim](https://github.com/isaacweber613/whisper-verbatim).

## Contact

For privacy questions, open an issue at [github.com/isaacweber613/whisper-verbatim/issues](https://github.com/isaacweber613/whisper-verbatim/issues).

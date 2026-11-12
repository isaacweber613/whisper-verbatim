# Play declarations draft and source evidence

Prepared November 10, 2026 against app version 0.8.4, versionCode 31. These are working answers for Isaac to review, not legal attestations or a completed Play Console form. Review the final release source and SDK configuration again if they change.

## Data safety

The app transmits data off the phone. Answer **Yes** to collection. Random or hashed identifiers still count as collected data. Local-only transcription history and the stored API key are not themselves collected by the developer; the key authenticates directly to OpenRouter when supplied.

| Play data type | Evidence and receiver | Draft purpose | Optional / required and retention |
| --- | --- | --- | --- |
| Audio files: voice or sound recordings | Cloud transcription sends recorded audio through OpenRouter to the chosen model. With no key, the Play free-minute Worker proxies it. `TranscriberClient.kt`, `WhisperAccessibilityService.kt`, `server/src/index.ts`. Yiddish audio goes through its Worker to the developer's PC, `YiddishServer.transcribe`. | App functionality; Yiddish retained training also improves the speech model. | Audio is required for dictation. Yiddish PC route is optional. Do not mark the data type ephemeral: Yiddish audio is retained. Verify provider retention for other routes. |
| App activity: other user-generated content | Yiddish model text and edited words, with take ID/outcome and appended flag, are sent to the PC. `YiddishServer.sendCorrection` and correction tracking in `WhisperAccessibilityService.kt`. | App functionality and model improvement. | Yiddish route optional; retained. No retention limit or deletion promise is established in this checkout. |
| App activity: app interactions | PostHog records app lifecycle, recording starts, transcription successes/failures, model, seconds, error code, insertion method and analytics switch-off. `Analytics.kt` and calls in `WhisperAccessibilityService.kt`. | Analytics. | Optional via Share usage data; on by default. Event when switching off is sent before opt-out. Verify hosted retention. |
| App info and performance: diagnostics | PostHog event properties include Android version, phone model/maker, app version/build/flavor, error codes. No crash reporter is implemented in this app. | Analytics. | Optional via analytics switch. Confirm exact SDK automatic properties and hosted retention before filing. |
| Device or other IDs | PostHog random install ID; random Yiddish install UUID and app version; free-minute SHA-256 hash of app-scoped Android ID, sent in `X-Device-Id` and stored with UTC month and seconds in D1. `Analytics.kt`, `YiddishServer.installId`, `FreeMinutes.deviceId`, `server/src/index.ts`, `server/schema.sql`. | Analytics for PostHog; app functionality for training grouping and monthly quota. | Analytics and Yiddish IDs optional. Free-minute ID required for that route; own-key cloud route avoids it. Confirm final form treatment where a data type serves both required and optional routes. D1 has no cleanup shown. |

Do not answer **No data shared** until processor terms are checked. OpenRouter, chosen model providers, PostHog, and Cloudflare may qualify as service providers under Google's definition only if they process solely on the developer's instructions. User-directed insertion into another app may qualify for the user-initiated transfer exception. These are form-specific classifications, not reasons to omit the actual transfers from the privacy policy.

All configured network endpoints use HTTPS. That supports encryption in transit, but does not prove every provider's handling, retention, or an independent security review. Check PostHog's hosted IP/geolocation settings before answering Location: inferred approximate location must be declared if collected. Receiving an IP address alone is not evidence that this app stores inferred location.

The app has no user accounts. Do not claim an account-deletion feature. Local history deletion does not delete PC-held Yiddish audio or server quota records. A working process for requests to delete server-held personal data and documented retention must be decided before claiming deletion is available.

## AccessibilityService declaration

Draft primary purpose: general-purpose dictation into other apps. Do not claim `isAccessibilityTool=true` based on disability support alone; this release does not declare that flag and is not presented as a disability-specific tool.

Draft explanation:

> Word for Word uses AccessibilityService to detect the focused text field and keyboard state, show a floating microphone button, and insert text that the user explicitly dictates. It reads the focused text field to preserve existing text and position the insertion. Optional key filtering detects the shortcut selected by the user. After a user starts a Yiddish-model take, the app observes edits to that take's text field and sends corrected text and take outcomes to the developer's Yiddish training server. It does not use Accessibility to autonomously plan or perform actions. The user starts and stops each recording.

Fixed in the Play build on November 11, 2026 (the text now names the Yiddish correction path and no longer claims audio goes only to OpenRouter). The defect was at `MainActivity.kt:538`: it says the app never saves or shares text-box contents and sends audio only to OpenRouter with the user's key. Both statements conflict with the Yiddish correction path and Play free-minute proxy. Correct before submission. The current disclosure has explicit Agree/No thanks actions, but inaccurate text does not satisfy informed consent.

Google requires the Accessibility disclosure to remain separate from other sensitive-data disclosures. Show what Accessibility accesses, why, and any associated sharing. Separately obtain clear informed consent before the optional Yiddish route retains audio and edited text for training. A store listing or privacy link alone cannot replace the in-app disclosure.

Review video must show opening the app; reaching and reading the full Accessibility disclosure; declining and triggering it again; accepting and enabling the service; and using dictation. Use captions or voice-over when service behavior is unclear. Record the final app after fixing the disclosure.

## Privacy text gaps to resolve

1. Both privacy versions omit the free-minute hashed Android ID and monthly D1 usage records. State what is stored and why.
2. Explain that the developer's free-minute server processes non-Yiddish recordings in transit. The blanket statement that the developer never receives non-Yiddish recordings is misleading for that route.
3. State retention and deletion handling for Yiddish audio, model output, corrected words, random install ID, monthly quota records, and analytics. Do not invent periods.
4. Explain that deleting local history or uninstalling does not delete those remote records. The quota hash is designed to survive reinstall for this app/signing identity.
5. Explain the Yiddish corrected-text transmission fully. Model training and human review are not the same purpose as delivering a transcription.
6. Publish a public, accessible privacy URL and make it reachable from inside the app, as well as the store listing. This checkout currently has no in-app privacy link in `MainActivity.kt`.

## Sources

Official Google help retrieved November 10, 2026 using agent-reach's Jina reader route:

- Data safety definitions, ephemeral processing, pseudonymous identifiers, sharing exceptions and purposes: https://support.google.com/googleplay/android-developer/answer/10787469?hl=en
- Accessibility eligibility, separate prominent disclosure, consent and review video: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en

Code evidence is from this checkout. Yiddish storage/training is also recorded in the separate model repository's `AGENTS.md` and `docs/TRAINING.md`; no claim about deployed retention or live access controls was independently verified here.

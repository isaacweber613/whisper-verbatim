# Play Store launch checklist

Agreed scope, November 10, 2026: prepare launch assets and forms, publish the privacy policy, rebuild the signed Play bundle, and advance personal developer signup up to payment. Isaac reviews legal declarations before submission. Do not send files to his phone unless asked.

## Before uploading a review candidate

- [ ] Complete developer-account registration, payment and identity/device verification as required by the actual Play Console account.
- [x] Fix inaccurate in-app Accessibility disclosure (Play build, 2026-11-11). Keep its consent separate from other sensitive-data disclosures.
- [x] (Play build, 2026-11-11; Isaac: only what Play requires, so no cloud-dictation dialog and no settings row) Add informed consent before the optional Yiddish route uploads and retains recordings and corrected text for training.
- [ ] Resolve privacy retention/deletion details and free-minute identifier/usage omissions in `declarations.md`.
- [x] Publish and verify a public privacy URL, and link it from the app (Play build, 2026-11-11).
- [x] (built 2026-11-11 at 0.8.5, versionCode 32; rebuild after any later source change) Build the latest source with `scripts/play-bundle.sh`; verify upload signature, package, version and manifest. Upload `WordForWordPlay.aab`, never either GitHub APK.
- [ ] Check signed release manifest has no `REQUEST_INSTALL_PACKAGES` or debug-only exported receiver and no external update flow.
- [ ] Capture final app screenshots and disclosure/use video. Finish Play icon and feature graphic.
- [ ] Supply support email and confirm countries, pricing, audience, ads, content rating, app access and Data safety answers.
- [ ] Isaac reviews the concrete legal answers before they are submitted.

## Testing and production

- [ ] Create internal testing release for initial validation, then the required closed test if the account is subject to Google's new personal-account rule.
- [ ] Confirm the rule in the actual account. The previously identified rule is at least 12 opted-in testers continuously for 14 days; installation alone is not the stated criterion, and elapsed time alone does not guarantee production access.
- [ ] Exercise microphone permission, Accessibility accept/decline, text insertion, shortcut, free-minute usage/exhaustion, own-key cloud route, Yiddish route/correction consent, offline fallback, and local history deletion.
- [ ] Apply for production access after eligibility, with truthful test results and feedback. Google reviews the application.
- [ ] Submit production only after required approvals and Isaac's review of legal declarations. Verify actual availability before describing the app as launched.

The existing GitHub APK release workflow remains separate from uploading the Play flavor. A store listing preparation change does not require a new APK release. Any subsequent app source change follows the repository's rule to release both GitHub APK flavors together.

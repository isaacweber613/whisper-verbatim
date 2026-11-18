# Play Store launch checklist

Agreed scope, November 10, 2026: prepare launch assets and forms, publish the privacy policy, rebuild the signed Play bundle, and advance personal developer signup up to payment. Isaac reviews legal declarations before submission. Do not send files to his phone unless asked.

## Before uploading a review candidate

- [ ] Complete developer-account registration, payment and identity/device verification as required by the actual Play Console account.
- [x] Account made and $25 paid; ID uploaded and website https://nuchemweber.com/wordforword/ verified (2026-11-14).
- [x] WordForWordPlay.aab built from 0.8.6 (versionCode 33) with play-bundle.sh, 2026-11-15. Two phone screenshots in docs/play-store/screenshots/ (emulator; 2026-11-17 resized to Play's 9:16: 1341x2384 and 1512x2688).
- [x] Device check done in the Play Console app (2026-11-15).
- [x] ID approved and contact phone verified (2026-11-16). After entering the text code, press Save changes or it is lost.
- [x] App ID com.isaacweber.wordforword: Create app said "already in use" because the GitHub builds have installs. Registered it under Android developer verification with the GitHub signing key (the PC debug key, SHA-256 90:79:A6:...:BE:1A) and a proof APK: a release build with assets/adi-registration.properties holding the console snippet, signed with that key. Registered 2026-11-17. App created 2026-11-17 (app id 4974807179218483861): Word for Word, en-US, App, Free; the three declarations accepted with Isaac's OK.
- [x] Default store listing saved as draft (2026-11-17): name, short and full description from listing.md, icon, feature graphic, 2 phone screenshots. Saved out of draft the same day, AI asset label: none (graphics drawn by scripts/play-graphics.py, screenshots are real). Until Google's first review, which comes with the closed testing release, internal testers see the temporary name 'com.isaacweber.wordforword (unreviewed)' and the default icon.
- Trader status: Non-trader (Isaac, 2026-11-15: "nobody is allowed to see my address"). Google lets a non-trader charge only if the app is not offered in the EEA or the UK at all, so exclude those countries before any paid feature. A trader's phone number and address are shown to everyone from March 1, 2027.
- [x] Fix inaccurate in-app Accessibility disclosure (Play build, 2026-11-11). Keep its consent separate from other sensitive-data disclosures.
- [x] (Play build, 2026-11-11; Isaac: only what Play requires, so no cloud-dictation dialog and no settings row) Add informed consent before the optional Yiddish route uploads and retains recordings and corrected text for training.
- [ ] Resolve privacy retention/deletion details and free-minute identifier/usage omissions in `declarations.md`.
- [x] Publish and verify a public privacy URL, and link it from the app (Play build, 2026-11-11).
- [x] (built 2026-11-11 at 0.8.5, versionCode 32; rebuild after any later source change) Build the latest source with `scripts/play-bundle.sh`; verify upload signature, package, version and manifest. Upload `WordForWordPlay.aab`, never either GitHub APK.
- [ ] Check signed release manifest has no `REQUEST_INSTALL_PACKAGES` or debug-only exported receiver and no external update flow.
- [ ] Capture final app screenshots and disclosure/use video. Finish Play icon and feature graphic.
- [x] App content forms saved 2026-11-17 (Isaac: "Fill it in"), all 10 done. Countries still open (Free app; exclude EEA/UK before any paid feature while Non-trader).
  - Privacy policy: https://nuchemweber.com/wordforword/privacy. Ads: none. Sign in details: nothing restricted.
  - Content rating (IARC, All Other App Types, contact micromaxpro7@gmail.com): all No; result Everyone / PEGI 3 / USK 0 / 3+.
  - Target age: 13-15, 16-17, 18 and over. Advertising ID: No (no AD_ID in the merged Play manifest). Government: No. Financial: none. Health: none.
  - Data safety: collects data, encrypted in transit (all endpoints HTTPS), no accounts, deletion requests via the privacy page. Nothing marked shared: OpenRouter, model providers, PostHog and Cloudflare treated as service providers. Collected, none ephemeral:
    approximate location (PostHog GeoIP from the IP; optional; analytics), voice recordings (required; app functionality),
    diagnostics and app interactions (optional; analytics), other user-generated content (Yiddish corrections; optional; app functionality),
    device or other IDs (required, the free-minute hash; app functionality and analytics).
  - Store settings: category Productivity; contact micromaxpro7@gmail.com, website https://nuchemweber.com/wordforword/, no phone.
- [ ] Isaac reviews the concrete legal answers before they are submitted.

## Testing and production

- [~] Create internal testing release for initial validation, then the required closed test if the account is subject to Google's new personal-account rule.
  - 2026-11-17: internal test live. 0.9.0 (versionCode 37) built from 49676f2 in a clean worktree (other tasks had uncommitted app edits), signed with the upload key (SHA-256 F6:13:90:...:08:99); Play App Signing holds the app key.
    Testers: email list "Internal testers" (ynbsd6242@gmail.com). Join link: https://play.google.com/apps/internaltest/4701430978639891994
    Play's automatic protection (installer check) was turned off: it needs minSdk 24 and the app supports Android 6 (minSdk 23).
- [ ] Confirm the rule in the actual account. The previously identified rule is at least 12 opted-in testers continuously for 14 days; installation alone is not the stated criterion, and elapsed time alone does not guarantee production access.
- [ ] Exercise microphone permission, Accessibility accept/decline, text insertion, shortcut, free-minute usage/exhaustion, own-key cloud route, Yiddish route/correction consent, offline fallback, and local history deletion.
- [ ] Apply for production access after eligibility, with truthful test results and feedback. Google reviews the application.
- [ ] Submit production only after required approvals and Isaac's review of legal declarations. Verify actual availability before describing the app as launched.

The existing GitHub APK release workflow remains separate from uploading the Play flavor. A store listing preparation change does not require a new APK release. Any subsequent app source change follows the repository's rule to release both GitHub APK flavors together.

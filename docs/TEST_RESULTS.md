> Historical baseline. The implemented browser/JNI proof and current device results are recorded in [PLAYBACK_FEASIBILITY.md](PLAYBACK_FEASIBILITY.md).

# Validation results ? 2026-11-23

## Environment

Windows 11 amd64; original Gradle9.5.0/AGP9.3.3 project with Java25 daemon criteria, target SDK37, final compile SDK37.1 and minSdk29. Java17 wrapper launcher successfully delegates to the installed Java25 daemon. No Git repository was present. Native research environment: cargo1.99.1, host Windows MSVC target only, NDK27.0.12077973; cargo-ndk and Android Rust targets are absent. No Rust native build was attempted.

## Completed checks

- Original untouched project: `:app:assembleDebug` PASS.
- Implemented prototype: `:app:assembleDebug` PASS.
- `:app:testDebugUnitTest`: 5 tests, 0 failures/errors. Native queue eligibility excludes YouTube even with an injected media URI, rejects unavailable/missing media, validates queue bounds; playlist names are normalized/validated; API errors distinguish expiry/quota/permission/missing content/server failure.
- `:browser-lab:testDebugUnitTest`: 4 tests, 0 failures/errors. HTTPS/domain boundaries, credential and scheme rejection, excluded YouTube/login domains, fixture rules and malformed input.
- `:app:assembleDebugAndroidTest` PASS. Compiled tests are not executed device tests.
- `:app:lintDebug` PASS, no blocking lint errors. Final SARIF contains exactly 3 warnings, all newer-build-tool notices: application/library AGP9.5.2 and Compose compiler2.4.21. The existing AGP9.3.3/Kotlin2.2.10 pairing is intentionally preserved; no application-code lint findings remain. The initial report had 19 warnings; runtime updates and template cleanup resolved the other findings.
- `signingReport` PASS; actual debug SHA-1 is recorded in GOOGLE_SETUP.md. Release signing is not configured.
- Original media generation completed: three 32-second mono 16-bit PCM WAV files at 22050Hz and three original PNG covers. No external recording was downloaded. Independent WAV validation found 32.0 seconds per file, nonzero signal, peaks 6366/6397/6489 (16-bit range), and no clipped samples. All application XML files parsed successfully.

## Final source validation

PASS: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :browser-lab:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --console=plain --offline --max-workers=2`.

Gradle reported `BUILD SUCCESSFUL in 4m 33s`, 127 actionable tasks (56 executed, 71 up-to-date). Final XML reports confirm 9 tests total, 0 failures/errors. Final SDK37.1 AAR metadata checks passed. Kotlin compilation emits deprecation notices for backward-compatible Material3 ListItem/Slider overloads and the v1 Compose test rule; these APIs still compile. They are migration work rather than successful runtime tests.

Final debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 86,539,913 bytes. Device-test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, 4,669,763 bytes. These are debug artifacts, not a production-signed release.

Final ADB check still returned `emulator-5554 unauthorized`. Six instrumented tests across five classes compile but have not run. No runtime/device pass is implied by final build success.

## Device checks not executed

`adb devices -l` repeatedly reported `emulator-5554 unauthorized`, including after `adb reconnect offline`. The user offered to authorize it; successful authorization has not yet been observed. No app install, launch, screenshot, audio playback, device rotation, WebView runtime test or device test pass is claimed.

Compiled instrumentation covers Room persistence across database reopen, deduplication/reorder/delete cascade; a real controller/service command sequence (play/pause/seek/previous/next/shuffle/repeat/queue); local search/empty/error/cancelled authorization state; Compose search/navigation/recreation and playlist creation; browser fixture interception and recreation; original package ID. These require an authorized emulator/device to run.

Bluetooth, headphone routing, focus, background/lockscreen behavior and Gemini require physical-device acceptance. Memory-leak and PSS/peak-memory measurements have not been run. See MANUAL_DEVICE_CHECKLIST.md.

## Cloud and browser scope

No Google Cloud OAuth Android client/account configuration was supplied. No live Google authorization or YouTube cloud mutation was attempted. Client code compilation and error helper unit tests do not validate the live integration. See GOOGLE_SETUP.md for exact remaining setup.

The reference repositories were inspected, not built or benchmarked. Brave Core reported an incomplete Windows checkout; inspected files are listed in OPEN_SOURCE_EVALUATION.md. No native Rust filtering library is shipped, so no ABI, 16KB-page, native concurrency, scriptlet or real-site ad-block effectiveness claim is made.

## Build issues encountered and corrected

One build failed parsing AndroidManifest.xml while source changes were in flight; subsequent builds parsed/merged it successfully. Another exposed conflicting default arguments inherited by the catalogue/search contracts; MusicSearchService now extends MusicCatalogProvider, and subsequent builds passed. A sandbox-only wrapper download failed with permission-denied networking; validation was rerun using approved Gradle access. A long-running repeat lint analysis was interrupted after two thread snapshots showed Kotlin PSI comment traversal; its identified Muse daemon was stopped, and this interrupted run is not a pass. The latest Compose update also correctly failed the AAR metadata gate against compile SDK37.0, requiring the already-installed SDK37.1; compileSdk was updated while retaining targetSdk37/minSdk29. These failed/interrupted attempts are not counted as successful tests.

## Evidence locations

- `app/build/test-results/testDebugUnitTest/TEST-com.allen.vybe.QueueAndApiTest.xml`
- `browser-lab/build/test-results/testDebugUnitTest/TEST-com.allen.vybe.browser.BrowserPolicyTest.xml`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `browser-lab/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html` and `.sarif`
- `app/schemas/com.allen.vybe.data.MuseDatabase/1.json`
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## OAuth follow-up validation

See OAUTH_VERIFICATION.md for current results superseding the initial device-access block: an authorized API33 physical phone is now connected. The debug APK was rebuilt, installed preserving data and launched. The user reported an authorization error; UI showed only a generic app error. Added safe numeric Google status diagnostics without changing the AuthorizationClient architecture. Diagnostic build/test-APK compilation passed in 1m52s; 12 local tests now have 0 failures/errors. Diagnostic APK installed and launched successfully; live Google status retry and playlist access remain pending user interaction. MIUI rejects remote input injection, so no account chooser/consent was automated.

Follow-up playlist-refresh error correction: debug APK and app unit tests passed in 54s; 10 app tests, 0 failures/errors, including missing-channel and unknown404 regressions. Phone disconnected before validation; new API diagnostic build has not been installed there. User reports the original generic404 immediately after connecting; channel setup is a possibility, not a confirmed diagnosis. See OAUTH_VERIFICATION.md.

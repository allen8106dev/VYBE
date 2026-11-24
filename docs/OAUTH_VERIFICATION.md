# Google OAuth configuration verification - 2026-11-23

## Architecture and configuration

User confirmed the pre-created OAuth client is type Android. The app keeps its existing Google Identity Services AuthorizationClient architecture and YouTube Data API repository. Requested scope is `https://www.googleapis.com/auth/youtube.force-ssl`, supporting implemented playlist reads/writes.

There is no Android OAuth Client ID field to populate in the project. Registration is in Google Cloud Console > Google Auth Platform > Clients > existing Android client: package `com.allen.vybe` plus the debug signing SHA-1 below. No missing runtime JSON/resource/properties file is required or consumed. No backend/offline-code/ID-token flow is implemented, so no separate Web client is required for current playlist access. See GOOGLE_SETUP.md for the exact console fields and primary documentation links.

The Google Cloud client's package/SHA-1, consent-screen audience, test-user list, configured scopes and enabled YouTube Data API cannot be verified from this local workspace. They must belong to the same Cloud project. The user's client ID string was neither needed nor requested.

## Build and signing evidence

Command: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :browser-lab:testDebugUnitTest :app:assembleDebugAndroidTest signingReport --offline --console=plain --max-workers=2`.

Result: BUILD SUCCESSFUL in 38s; 101 actionable tasks (9 executed, 92 up-to-date). The 9 existing unit tests remained up-to-date; they were not re-executed in this invocation. No new production authentication behavior was introduced by documentation clarification.

Actual debug SHA-1: `D3:1C:6B:E5:4C:CF:79:E8:29:6E:43:2A:D4:C2:5C:7E:E1:BC:5B:3C`.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Physical phone checks

- Device: M2101K7BI, Android API33, authorized wireless ADB. Google Play services package is installed.
- Rebuilt APK installed with `adb install -r`: Success, preserving existing application data.
- Cold launch of `com.allen.vybe/.MainActivity`: Status ok, total launch time 2652ms.
- UI hierarchy confirms Muse Home and Settings control rendered.
- Automated navigation attempt failed: MIUI denied shell input injection with SecurityException/INJECT_EVENTS. No device security settings were changed or bypassed.
- User was asked to open Settings > Connect YouTube > Agree and connect and complete Google account selection/consent on the phone.

The user attempted the flow and reported a Google error. The subsequent UI hierarchy showed only the app's generic authorization failure, so the underlying Google status could not be determined. Live OAuth success is NOT verified. Account chooser, consent success, access-token receipt, real playlist refresh, expiry and playlist mutations are NOT yet verified. App installation, launch and a successful build do not prove Google authorization works.

## Diagnostic improvement and rebuild

Retained AuthorizationClient and YouTube playlist scope. The Activity now parses a non-null authorization result even when the result code is not RESULT_OK, allowing Google ApiException status to reach the UI. The ViewModel exposes only the numeric status with an actionable message; it does not display/log raw exceptions, account data or tokens. Missing result data remains a non-specific incomplete-flow message.

Added 3 local tests for configuration status10, cancellation16/12501, network7 and unknown/missing status. Rebuilt with `:app:assembleDebug :app:testDebugUnitTest :browser-lab:testDebugUnitTest :app:assembleDebugAndroidTest --offline --console=plain --max-workers=2`: BUILD SUCCESSFUL in 1m52s. XML reports show 12 tests total, 0 failures/errors (8 app tests and 4 browser policy tests; browser task up-to-date). These tests validate safe error presentation, not live Google consent.

Diagnostic APK installed successfully with adb install -r. Relaunch succeeded (cold launch, 2898ms). User was asked to retry and report the exact status displayed. The exact Google error and successful playlist access remain pending; no Cloud Console configuration change or successful OAuth is claimed.

## Observed diagnostic retry result

Read the on-device UI hierarchy after retry: `Google authorization failed (status 8). ...` and `Connect YouTube` remained visible. Thus live authorization did not succeed. Google CommonStatusCodes defines 8 as INTERNAL_ERROR; this generic result does not establish a SHA-1 mismatch, missing Web client, missing configuration file or any single cause. Do not confuse it with DEVELOPER_ERROR/status10. No token was obtained or playlist operation verified in this check.

Next: verify the existing Android client's package and exact signing SHA-1 in the same Cloud project that has YouTube Data API v3 enabled, confirm consent-screen audience/test-user eligibility, then retry. Device Play services/network issues may also need investigation. No credentials should be pasted into repository or chat. Reference: https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes .

## Follow-up: failure while refreshing playlists after connection

User reports the missing playlist/video message immediately after Google connection. Code inspection shows connect stores an access token and immediately calls playlists.list with mine=true; the old message incorrectly mapped every HTTP404 to a deleted playlist/video. This symptom suggests progress beyond the earlier authorization failure, but the exact API reason is still unobserved.

Updated error handling to distinguish channelNotFound/youtubeSignupRequired from playlistNotFound/videoNotFound/playlistItemNotFound. Unknown HTTP404 now says requested resource rather than inventing deletion. API failures include fixed method/endpoint, HTTP status and a bounded machine-readable reason; tokens, parameters, account details and raw response bodies are excluded. Added regression tests for missing-channel versus unknown404 behavior.

Google playlists.list documents channelNotFound and playlistNotFound separately: https://developers.google.com/youtube/v3/docs/playlists/list . Suggested account check: open YouTube with the same Google account, visit Your channel, and complete setup only if prompted. Missing channel is a hypothesis, not a confirmed root cause.

Physical phone is no longer listed in adb devices; only the unauthorized emulator remains. The new error cannot be installed/reproduced on the phone until it reconnects. Updated debug APK and app unit tests passed: BUILD SUCCESSFUL in 54s. The 10 app tests passed with 0 failures/errors, including both new API-error regressions. The APK has not been installed on the disconnected phone and live playlist access remains unverified.

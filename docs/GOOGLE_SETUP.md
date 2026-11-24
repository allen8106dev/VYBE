# YouTube account setup

No API key, client secret or access token is hardcoded. OAuth AuthorizationClient identifies the Android app through its package and signing certificate. Muse does not need a client secret or google-services.json for the implemented authorization-only flow.

## Exactly where the OAuth client is configured

For this existing authorization-only architecture, **do not paste the Android OAuth Client ID into Kotlin, strings.xml, local.properties, a manifest meta-data entry or google-services.json**. There is no client-ID parameter in the access-token request used by Muse. The registration is on Google's side: **Google Cloud Console > Google Auth Platform > Clients > your existing Android client** (older console layout: APIs & Services > Credentials).

Verify these fields on that Android client:

| Field | Exact value for the rebuilt debug APK |
|---|---|
| Application type | Android (confirmed by the user) |
| Package name | `com.allen.vybe` |
| SHA-1 certificate fingerprint | `D3:1C:6B:E5:4C:CF:79:E8:29:6E:43:2A:D4:C2:5C:7E:E1:BC:5B:3C` |

The Client ID remains in Cloud Console; Google Play services resolves the Android registration using the calling package and signing certificate. Creating an Android client for a different package/signature does not configure this APK. The signing fingerprint was rechecked using `signingReport`. The Cloud Console fields themselves cannot be inspected from this workspace.

No missing runtime configuration file is needed for the current flow. A downloaded OAuth client JSON is not loaded by the app. Adding an unused `oauth_client_id` string would not change authentication. Firebase/Google Services Gradle configuration is not part of this architecture.

## Is a separate Web OAuth client required?

**No, for the current on-device YouTube access-token flow.** `MuseViewModel.authorize()` requests `youtube.force-ssl` through `AuthorizationClient`; `MainActivity` launches its PendingIntent and returns `AuthorizationResult.accessToken`; `YouTubeRepository` sends the token as a Bearer header to the official Data API. The app has no backend, server auth-code exchange, refresh-token storage or ID-token login.

A Web client would be required if adding backend authorization-code/offline access through `requestOfflineAccess(serverClientId)`. A separate Credential Manager Google ID-token login would also use a server/Web client ID as its audience; it is not needed merely to authorize the playlist operations implemented here. Never use an Android client ID in a server-client-ID field or ship a client secret in the APK.

## Cloud configuration checklist

1. In a Google Cloud project enable YouTube Data API v3.
2. Configure Google Auth Platform branding, support contact, privacy/terms URLs, audience and test users. Add your own Google account as a test user when the consent screen is in testing mode.
3. Create an Android OAuth client with package `com.allen.vybe` and the SHA-1 of the installed build. Run `./gradlew signingReport` (`.\gradlew.bat signingReport` on Windows); register the debug SHA-1 for debug installs and the release SHA-1 separately for release builds.
4. Configure the required scope `https://www.googleapis.com/auth/youtube.force-ssl`. It permits playlist reads/writes; Google may require verification for broader distribution. Personal use does not waive API policies.
5. Install on a device/emulator with Google Play services. Open Settings > Connect YouTube > read and accept the displayed data-use notice > choose an account and grant authorization.
6. In Search choose YouTube and submit a query. Results open in YouTube/external browser. In Your library refresh cloud playlists; create a *private* test playlist, add/remove/reorder a video, rename, verify in YouTube, then delete the test playlist.
7. Disconnect clears app memory and requests cached token removal. Use Manage or revoke Google access to revoke the Google-side grant. Reconnect obtains another token; HTTP401 invalidates app state and clears the invalid cached token before the next authorize call.

Tokens are memory-only, so reconnect after process death. No server or refresh token is used. Search consumes quota only on submit/load-more; mutations are never blindly retried. An interrupted insert can have succeeded remotely; refresh before retry. Muse rejects duplicate video IDs on add, but separately retains playlist-item IDs so existing duplicate entries can be removed independently. External concurrent edits can still race the duplicate check; server data is authoritative.

Unverified without your project/account: consent, token acquisition/expiry, quota errors, all actual cloud mutations, inaccessible/deleted videos, manual-sort requirements and multi-page account libraries. Authorizing the emulator alone does not supply OAuth setup. Do not paste access tokens or client secrets into chat or repository files.

Sources: [Android authorization](https://developer.android.com/identity/authorization), [playlistItems.update](https://developers.google.com/youtube/v3/docs/playlistItems/update), [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies).

## Fingerprint from this workstation

The successful `signingReport` reported this debug SHA-1 on 2026-11-23:

`D3:1C:6B:E5:4C:CF:79:E8:29:6E:43:2A:D4:C2:5C:7E:E1:BC:5B:3C`

Register it with package `com.allen.vybe` for this debug APK. Regenerating the debug keystore changes the fingerprint; rerun signingReport when in doubt. No release signing configuration exists.

## Verification for this configuration task

User confirmed that the existing OAuth client is type Android. Rebuild and signingReport passed; the fingerprint above matches the debug signing configuration. A physical M2101K7BI phone running Android API33 with Google Play services is connected. Device-flow results are recorded separately in `docs/OAUTH_VERIFICATION.md`; a successful build does not validate Google consent or live playlist access.

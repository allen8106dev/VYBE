# Muse progress ? 2026-11-23

## Delivered initial prototype

Preserved the original application ID, app module, Gradle wrapper, AGP, minimum/target SDK levels and Compose foundation. Raised compileSdk only to the already-installed 37.1 as required by current stable Compose; updated compatible runtime dependencies. Added an isolated browser-lab module, package-separated application layers and original Muse branding. This workspace had no Git repository; no commit or remote push was performed.

Source-level research completed for all four requested projects before production playback implementation. Brave Core cloned but its Windows working-tree checkout reported a failure; specific Shields sources used in the evaluation were present and inspected. No reference source copied into production. Reports distinguish source evidence from runtime measurements.

Native implementation: original bundled audio, ExoPlayer/MediaSessionService/MediaController, transport controls, editable queue, shuffle/repeat, focus and becoming-noisy handling, notifications/session metadata and paused queue restoration. Compose Home/Search/Library/Playlist/Artist/Now Playing/Queue/Settings screens call real state/repository operations. Room holds local playlists/favourites/artist follows/history; DataStore holds preferences and bounded recent searches.

YouTube implementation: user-consented AuthorizationClient, official REST search/videos/channels and playlist CRUD/item operations; pagination, canonical IDs, expiry/quota/network errors and duplicate checks. Responses/tokens remain in memory. User must configure Google Cloud Android OAuth with the installed signature. Videos open externally. OAuth setup and live account validation are not completed.

Browser implementation: controlled HTTPS fixture, small original host rule, counters, HTTPS navigation, JS opt-in, cookie/cache controls, permissions/TLS restrictions, pause/resume, renderer recovery and destruction. Native filter engine integration and media synchronization are deliberately not claimed.

## Feature matrix

WORKING = observed by the stated automated check, not a blanket device claim. PARTIALLY WORKING = implemented/compiling with runtime or scope gaps. BLOCKED = needs external setup. NOT TESTED = code exists but the relevant environment has not validated it.

| Feature | Status | Evidence / gap |
|---|---|---|
| Source evaluation and architecture | WORKING | Actual checked-out sources inspected; no upstream builds/benchmarks |
| Queue eligibility/boundary guards | WORKING | Local unit tests, native-only source invariant |
| API error classification and playlist name validation | WORKING | Local unit tests |
| Browser navigation/domain policy | WORKING | Local unit tests for invalid schemes, credentials and domain boundaries |
| Compose screens, navigation and actions | PARTIALLY WORKING | Debug compiles; emulator UI validation pending |
| Local playlist persistence/reorder/delete | NOT TESTED | Room schema exported; instrumentation tests written and compiled |
| Favourites, followed artists and play history | PARTIALLY WORKING | Backed by Room; device validation pending |
| Original native audio and queue controls | PARTIALLY WORKING | Real Media3 engine compiled; controller instrumentation awaiting device |
| Focus/headphones/background/notification/lockscreen/Bluetooth | NOT TESTED | Implemented through Media3; hardware/OS checks required |
| Rotation/process restoration | NOT TESTED | Saved navigation, ViewModel and paused sample snapshot implemented; device checks required |
| YouTube search and cloud playlist UI/client | BLOCKED | Needs Google Cloud OAuth setup and live account validation |
| Account consent/expiry/disconnect | BLOCKED | Implemented; credentials/Play services/device validation required |
| Original artist/collection discovery | PARTIALLY WORKING | Local original collection only; no fabricated YouTube albums/artists |
| Browser fixture/interception/lifecycle | PARTIALLY WORKING | Module compiles; instrumentation/device run pending |
| Rust/JNI filter engine | NOT IMPLEMENTED | Wrapper lifetime/ABI/16KB checks required first |
| Filter downloads/cosmetics/scriptlets | NOT IMPLEMENTED | Research only; no third-party lists bundled |
| Embedded YouTube/native audio extraction | NOT IMPLEMENTED | External YouTube playback boundary maintained |
| Browser ? Media3 adapter | NOT IMPLEMENTED | No demonstrated reliable state bridge |
| Gemini transport commands | NOT TESTED | Requires real compatible device; MediaSession compilation is insufficient |
| Voice play-by-track-name/media search | NOT IMPLEMENTED | Requires separate search routing/library-session work |
| Lyrics | NOT IMPLEMENTED | Original instrumentals show truthful unavailability |
| Leak/performance benchmarking | NOT TESTED | Device profiling checklist supplied |

## Next external steps

Authorize emulator-5554 (currently reported unauthorized), then execute instrumentation tests and visual/runtime checks. Configure the OAuth Android client as in GOOGLE_SETUP.md. Test Gemini and Bluetooth on a real compatible device. Finish device acceptance before treating this as a validated daily music app. Full scope remains incomplete where this matrix says so; the delivered milestone is an initial functional implementation and buildable prototype.

## Final automated gate

Debug APK and instrumentation APK built successfully with SDK37.1. All 9 local tests pass with 0 failures/errors. Android lint passes with 3 notices about newer retained build-toolchain versions and no application-code lint findings. Final validation completed successfully in 4m33s. Device tests still cannot run because ADB reports the emulator unauthorized. See TEST_RESULTS.md for exact commands, evidence and unexecuted checks.


## 2026-11-24 browser proof and native player

Implemented in-app YouTube webpage playback, retained browser service and platform MediaSession, actual Rust JNI filtering with official EasyList/EasyPrivacy snapshots, custom native player/queue/progress controls and local synced-lyrics import. A 4-test browser/player suite passed on the phone; 17 local tests passed. The persistent mini-player extension is built and installed, but its corrected fresh-Home harness awaits reconnection after wireless ADB disappeared. Build/lint pass; exact Brave parity, verified YouTube ad suppression, robust YouTube background/locked-screen playback and real Gemini voice results remain open. See PLAYBACK_FEASIBILITY.md for evidence, failures and policy implications.


## 2026-11-24 artwork player and browser revision

Added an opaque native artwork player, creator-channel thumbnail lookup, artwork mini-player and automatic LRCLIB lyrics matching. A browser-only document-start visibility/response-filter experiment supplements Rust network filtering. Native full-player/mini-player controls and live lyrics passed on the phone; actual YouTube progressed on Android Home and in Clock with the native player covering the browser. Exact Brave parity, actual YouTube advertising suppression, physical-lock playback and Gemini voice commands remain unverified/unmet. See [PLAYBACK_FEASIBILITY.md](PLAYBACK_FEASIBILITY.md) for current evidence; this entry supersedes earlier pending-mini-player/provider notes.


Latest retained-navigation revision: normal YouTube song playback now uses one MainActivity browser attachment across the native song page/Home/Search/Library. A real YouTube navigation test passed through those destinations; four focused device tests and 17 offline tests passed, with build/lint successful. Worker cosmetics, smaller decoded thumbnails and reduced notification churn address UI work. Performance still has measured stalls; physical-lock survival, complete startup unmuting, actual video-ad suppression, exact Brave parity and Gemini voice remain unresolved or awaiting manual checks. See PLAYBACK_FEASIBILITY.md for the current results rather than inferring completion from earlier milestones.

## 2026-11-24 YouTube-only player and library redesign

Removed the original-music catalogue, native sample player/service, source picker and original artwork/audio from the production app. The remaining WAV is a debug-only browser test fixture. Search always uses YouTube; app label is VYBE. Home, Library, playlist editing and Settings use a consistent dark green layout. Song menus support Play next, Add to queue, likes, playlist saving and sharing. Library supports local YouTube playlists, connected-account playlists, liked songs, recent playback, filtering and sorting. Room v2 stores song metadata; the v1 migration preserves playlist names and removes obsolete sample references.

The retained browser service now owns the editable queue, automatic next-song playback, shuffle, repeat all/one, queue moves/removal and clear-up-next. Sleep timers pause playback and can be cancelled. Queue edits preserve the current song. Browser focus and retained WebView navigation fixes remain in place. No native sample service competes for media focus.

Android assistant integration now exposes a platform MediaBrowserService with a restricted caller root, playable queue, metadata and play/pause/next/previous/seek callbacks. Both MediaSession playFromSearch and the exported MEDIA_PLAY_FROM_SEARCH activity route build a real YouTube result queue and play its first song. Voice requests share results with the Search screen to avoid a duplicate API request. Existing Google grants can be recovered silently; new consent remains an explicit action.

Validation: 25 unit tests passed, including 8 queue boundary/identity/shuffle/repeat tests. build-youtube-complete.log records the successful build and lint gate. MuseNavigationTest passed creation/rotation and Home/Search/Library/Settings navigation in device-youtube-ui-verified.log and device-youtube-complete.log. Library persistence (including likes and history) and AssistantAndQueueTest passed in device-youtube-complete.log. The old-database upgrade and strict audio-continuity tests passed in device-youtube-recovery.log (2 tests, 58.08 s): zero added pause/wait events, unmuted playback through player, Home, Search, Library and Android Home. AssistantAndQueueTest exercised real YouTube search, system pause/resume/next/previous, seek, natural-end advance, queue edits and timer setup/cancellation. Evidence is in artifacts/youtube-ui/assistant-queue-proof.json and audio-continuity.json. Earlier failures in the schema-fixture parser and UI selectors are retained in logs; they were test-harness issues corrected before the successful runs.

Manual Gemini result: the user confirmed pause, play and skip work. Particular-song requests do not work through Gemini. Initially Gemini heard VYBE as Wynk; subsequently it reported being unable to control VYBE for song playback. Successful Android handler tests must not be reported as successful Gemini song routing. No claim of full Gemini integration is made.

Follow-up implements a Search microphone and a launcher Voice search shortcut using the phone's speech-recognition activity. Recognized title/artist requests go to the same live YouTube search/play path. Cancellation should preserve current playback. VYBE does not request RECORD_AUDIO or record the microphone itself. An actions.intent.GET_THING declaration maps assistant search to results only; its presence is not evidence of Google-side activation. Google documents App Actions preview/account setup and Play publication/review requirements: https://developer.android.com/develop/devices/assistant/get-started and https://developer.android.com/develop/devices/assistant/test-tool. Gemini song routing remains an external integration gap; it cannot be enabled merely by changing this sideloaded APK.

## 2026-11-24 Hey VYBE and navigation follow-up

Home navigation now uses one saved screen stack with one visible screen and no navigation fades. Home resets that stack; Back returns through nested pages; rotation preserves the selected destination. BrowserEngineHost remains retained outside screen content.

Added an opt-in, on-device English Hey VYBE foreground microphone service. Vosk Android 0.3.75 plus the bundled Apache-2.0 small English model runs speech locally. Open-vocabulary song recognition is paired with a confidence-checked control grammar. The latter corrected a synthetic pause/cause ambiguity without truncating song names. Audio remains in memory and is neither saved nor uploaded. Recognized song-search text goes to YouTube. Microphone and notification permissions are requested when enabling the feature in Settings. An ongoing microphone notification and Settings toggle stop listening; the voice command "Hey VYBE, stop listening" also disables it. START_NOT_STICKY avoids silently restarting microphone capture after a process restart. The service holds a CPU wake lock while enabled and releases recording/model/decoder/lock resources on stopping; continuous listening uses extra battery. Android 10 uses the compatible foreground-service overload.

A shared voice dispatcher handles song play, search without replacing the current song, pause/resume/stop, next/previous/restart, shuffle, repeat, seeking, numeric/spoken-number sleep timers, and navigation to Home/Search/Library/Queue/player/Settings. Home and Search microphone buttons use the phone speech UI when wake listening is off, or arm the existing local listener when it is on, avoiding competing microphone sessions. This is direct VYBE voice control; Gemini song routing remains unverified and previously failed on this sideloaded app.

Validation: build-wake-ready.log records successful assemble, unit tests, and lint (0 errors; one wake-lock analyzer warning about cleanup inside runCatching, despite release in finally). All 18 app unit tests passed, including five voice-command/wake-gate tests. MuseNavigationTest passed in device-voice-navigation-verified.log. All three final device tests passed in device-wake-final.log (27.507 s): native offline recognition of synthetic wake/pause and Believer requests, rejection of speech without the wake phrase; real YouTube voice-dispatch pause/resume/shuffle/repeat/timers/search plus Home button/voice/rotation; injected Android recognizer results and cancellation preserving music. Earlier test failures were corrected: lazy-list scrolling, elapsed-vs-wall timer comparison, and lifecycle tracking after manually injecting onNewIntent. Evidence: artifacts/voice-assistant/*.json. Synthetic speech fixtures are androidTest-only.

The user enabled the actual microphone listener; Android diagnostics confirmed a running foreground microphone service, non-silenced VOICE_RECOGNITION capture, and the VYBE:voice wake lock. The media session subsequently showed the official Believer video playing. Audible experience, speaker interference, and locked-phone wake recognition still need the user's manual result; automated transcripts do not establish those outcomes.

User-requested wake phrase revision: replaced the phrase with "Hey Music" throughout detection, the parallel control grammar, Settings, notification, prompts and voice-disable feedback. The app is still named VYBE. This supersedes the Hey VYBE activation wording above; it does not change the song/transport dispatcher or Gemini integration status. build-hey-music.log records successful assemble, 18 app unit tests and lint. The live listener must be enabled again after installing the update, as intended for the explicit microphone session.

Final phrase validation: build-hey-music-stream.log passed build/unit/lint. device-hey-music-stream.log passed the native speech test in 8.775 s: "hey music" followed by "pause the music"; "hey music" followed by "play believer by imagine dragons"; no command for ordinary speech without the wake phrase. artifacts/voice-assistant/hey-music-recognition-proof.json contains the actual primary/control transcripts, word timing and selections. The earlier phrase tests exposed that flushing/resetting the parallel control recognizer at the open recognizer's silence endpoint could cut off the next word. The final implementation keeps streams continuous, selects control hypotheses with overlapping word times, and preserves an already recognized wake-only utterance. This final result supersedes the failed device-hey-music*.log attempts. The final APK was installed; the updated microphone session is off until the user enables Settings > Hey Music again.

## 2026-11-24 playlist and playback controls

Added quick like controls to the mini-player, full player, home track rows and playlist tracks, backed by local Room favorites. Playlist actions support changing name and description, reordering/removing tracks, adding songs, queueing the full playlist, making a local copy and sharing. Playlist track search and sorting were added. Voice commands can like or unlike the current track. Queue insertion skips tracks already present.

`build-library-player-features.log` records a successful assemble, unit-test, instrumentation APK and lint run for the primary feature changes. Playlist-copy persistence assertions and voice-like parser assertions were added after that run and remain unvalidated by a subsequent build. Wireless ADB disconnected before another device check. Gemini pause/play/skip are manually confirmed by the user; Gemini named-song search and playback remain unsupported in the current sideloaded setup. “Hey Music” is the separate VYBE wake phrase, not a Gemini integration.


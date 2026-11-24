# Muse architecture

Decision date: 2026-11-23. Preserve the existing `com.allen.vybe` application ID, SDK 29–37, AGP 9.3.3, Gradle 9.5 and Compose setup. Brand the app Muse. This directory was not a Git repository at inspection.

## Boundaries

Use one app module with domain, catalog, data, library, playback, media, settings and designsystem packages; an experimental `browser-lab` Android library and Java-only `filter-native` dependency boundary. Manual application-scoped dependency injection is sufficient at this size; do not add Hilt solely for ceremony.

Compose → Android ViewModel / StateFlow → repositories and PlaybackCoordinator. Room stores application-owned local playlists, favourite sample tracks and play history. DataStore stores preferences. Google tokens and YouTube API responses stay in memory, cleared on disconnect. Cloud playlist IDs, video IDs and playlist-item IDs remain distinct. No audio downloads.

`MusicCatalogProvider`, `MusicSearchService`, `PlaylistRepository`, `RecommendationService`, `PlaybackEngine` and `QueueManager` define the main seams. Recommendations are local discovery of original samples, never represented as YouTube recommendations. Channel results are labelled channels, not inferred music artists; albums are not invented.

## Playback decision before implementation

Native playback: Media3 ExoPlayer owned by MediaSessionService, reached through MediaController. Original synthesized audio bundled with the app allows offline testing without extraction. ExoPlayer owns queue order, shuffle/repeat, focus and becoming-noisy handling. MediaSession supplies notification and external transport controls. UI polls position only while collected. Never feed YouTube watch URLs into ExoPlayer.

YouTube: supported Data API search and account playlist operations, with AuthorizationClient user consent. Song taps pass IDs, title/creator metadata and the current list to BrowserMusicActivity, a native Compose player shell. BrowserPlaybackActivity remains the plain diagnostic surface. Its ordinary YouTube mobile webpage is separate from AuthorizationClient/REST credentials and from the native Media3 player. No official IFrame API player is loaded or filtered. This separation is an architecture boundary, not a policy exemption. See PLAYBACK_FEASIBILITY.md for the tested limits.

Browser: lifecycle-managed WebView in a separate diagnostics screen. HTTPS only, no native JavaScript bridge, no file/content access, no permission or certificate bypass. Browser filtering is experimental: the media research service enforces EasyList/EasyPrivacy through Rust on ordinary browser subresources, while the older generic diagnostics policy still excludes YouTube domains. No official IFrame API player is loaded or filtered. Prototype the smallest independently testable rules before integrating a native dependency. Source inspection identified a native close/query race risk in the published wrapper; synchronization and ABI verification are prerequisites to enabling it. The new retained browser service serializes all Rust calls including destruction, exposes a platform MediaSession, and reports observed HTMLMediaElement state rather than emulating a Media3 Player. Never claim Brave parity.

## State and failure handling

One-way UI events, observable Room flows, explicit search/account loading/error/empty states. Mutations are serialized, cloud refresh follows successful writes, no blind retries of inserts. API expiry clears the in-memory token and asks to reconnect. Cancellation must propagate. Search is explicit-submit to protect quota. All paginated library reads follow nextPageToken. Search fetches pages only on request.

## Validation

Compile the baseline then the end-to-end sample player frequently. Unit-test queue boundary rules and search/auth state helpers. Instrument Room and controller/service behavior separately. Real-device checklist covers sound, focus, Bluetooth, notification/lockscreen controls, background transitions, process death and Gemini. A build is not proof that these device behaviors work.

## Dependency verification

Runtime versions verified against upstream release pages on 2026-11-23: [Media3](https://developer.android.com/jetpack/androidx/releases/media3?hl=en) 1.11.1, [Room](https://developer.android.com/jetpack/androidx/releases/room) 2.8.5, [Navigation](https://developer.android.com/jetpack/androidx/releases/navigation) 2.10.2, [DataStore](https://developer.android.com/jetpack/androidx/releases/datastore) 1.3.0, [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) 2.11.1, Compose BOM 2026.11.01, [coroutines](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0) 1.11.0 and [Google authorization](https://developer.android.com/identity/authorization) 22.0.0. Preserve the existing AGP9.3.3/Kotlin2.2.10 compiler pairing and wrapper instead of doing an unrelated build-toolchain migration. Lint can report newer toolchain versions; upgrades require their own compatibility validation.

Compose BOM2026.11.01 resolves Compose1.13.0, whose AAR metadata requires compile SDK37.1. The first update build correctly failed this gate against SDK37.0; compileSdk was raised to the already-installed 37.1 in both modules. minSdk29 and targetSdk37 stay unchanged. This is a required compatibility change, not a runtime support-level change.


## 2026-11-24 artwork player and browser revision

Added an opaque native artwork player, creator-channel thumbnail lookup, artwork mini-player and automatic LRCLIB lyrics matching. A browser-only document-start visibility/response-filter experiment supplements Rust network filtering. Native full-player/mini-player controls and live lyrics passed on the phone; actual YouTube progressed on Android Home and in Clock with the native player covering the browser. Exact Brave parity, actual YouTube advertising suppression, physical-lock playback and Gemini voice commands remain unverified/unmet. See [PLAYBACK_FEASIBILITY.md](PLAYBACK_FEASIBILITY.md) for current evidence; this entry supersedes earlier pending-mini-player/provider notes.


Latest retained-navigation revision: normal YouTube song playback now uses one MainActivity browser attachment across the native song page/Home/Search/Library. A real YouTube navigation test passed through those destinations; four focused device tests and 17 offline tests passed, with build/lint successful. Worker cosmetics, smaller decoded thumbnails and reduced notification churn address UI work. Performance still has measured stalls; physical-lock survival, complete startup unmuting, actual video-ad suppression, exact Brave parity and Gemini voice remain unresolved or awaiting manual checks. See PLAYBACK_FEASIBILITY.md for the current results rather than inferring completion from earlier milestones.

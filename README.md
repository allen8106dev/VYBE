# VYBE

An Android YouTube music player with playlists, a queue, voice controls and an opt-in offline wake phrase. Application ID: `com.allen.vybe`. Kotlin, Compose/Material3, Room, DataStore and Media3. Android 10/API29 minimum, SDK37 target, compile SDK37.1.

## Try it

Open this directory in Android Studio, sync, and run `app` on an authorized device. Search and playback use YouTube. Connect a Google account to search and manage YouTube playlists. You can also create local playlists, like tracks, edit playlist details and contents, make a local copy, share a playlist, and queue or shuffle a playlist. Likes and local playlists are stored on the device.

YouTube cloud operations require [Google Cloud setup](docs/GOOGLE_SETUP.md). YouTube plays in the app's retained browser player. VYBE does not download YouTube audio. See [device playback findings](docs/PLAYBACK_FEASIBILITY.md) for platform limitations and measured results.

Settings ? Browser lab opens an independent WebView fixture with an original host-block rule and request diagnostics. The added media proof uses the pinned Rust engine through a Java/JNI boundary, a controlled HTML-audio fixture, and a separate platform MediaSession. The media research engine now enforces bundled EasyList/EasyPrivacy subresource rules, with a native player, browser mini-player and local timed-lyrics import. Exact Brave equivalence and YouTube video-ad blocking are not established. The older generic diagnostics fixture retains its original host rule.

## Build and test (Windows)

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :browser-lab:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat signingReport
```

Use the project's daemon JVM criteria (Java25). The machine inspected already had that runtime and SDK37.1; a Java17 launcher delegates to the configured daemon. Do not silently downgrade Gradle/AGP or change the app ID to resolve local setup. First dependency resolution needs network access.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Debug contains development tooling and unshrunk icon classes; no release signing key is configured. The existing release optimization setting was preserved.

## Evidence and remaining work

- [Progress and feature matrix](docs/PROGRESS.md)
- [Test results](docs/TEST_RESULTS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Open-source evaluation](docs/OPEN_SOURCE_EVALUATION.md)
- [Browser research](docs/BROWSER_RESEARCH.md)
- [Device checklist](docs/MANUAL_DEVICE_CHECKLIST.md)
- [Notices](docs/THIRD_PARTY_NOTICES.md)

Research clones and generated artifacts are excluded from source control. No credentials are checked in.


## 2026-11-24 artwork player and browser revision

Added an opaque native artwork player, creator-channel thumbnail lookup, artwork mini-player and automatic LRCLIB lyrics matching. A browser-only document-start visibility/response-filter experiment supplements Rust network filtering. Native full-player/mini-player controls and live lyrics passed on the phone; actual YouTube progressed on Android Home and in Clock with the native player covering the browser. Exact Brave parity, actual YouTube advertising suppression, physical-lock playback and Gemini voice commands remain unverified/unmet. See [PLAYBACK_FEASIBILITY.md](docs/PLAYBACK_FEASIBILITY.md) for current evidence; this entry supersedes earlier pending-mini-player/provider notes.


Latest retained-navigation revision: normal YouTube song playback uses one MainActivity browser attachment across Home/Search/Library. A real YouTube navigation test passed through those destinations; four focused device tests and 17 offline tests passed, with build/lint successful. See PLAYBACK_FEASIBILITY.md for current playback findings.

## 2026-11-24 library and player controls

Added track likes on the mini-player, full player, home lists and playlist rows. Liked Songs stays local to the device. Playlist actions now include editing name and description, editing song order/removing tracks, adding songs, queueing the full playlist, making a local copy and sharing. Playlist pages can search and sort tracks. Voice controls can like and unlike the current song. Queue insertion skips tracks already in the queue.

`build-library-player-features.log` records a successful debug build, unit test, instrumentation APK build and lint run for the library/player changes. A few small follow-up changes, including playlist-copy persistence assertions and voice-like parser assertions, were made after that run and have not been rebuilt. Device validation is also pending because the wireless Android device disconnected. Gemini has controlled pause/play/skip, but direct Gemini requests to find and play a named song remain unsupported in the current sideloaded setup. The VYBE voice assistant and its “Hey Music” wake phrase are separate from Gemini.

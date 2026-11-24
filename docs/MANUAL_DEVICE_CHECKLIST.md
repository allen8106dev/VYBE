# Device acceptance checklist

Record device model, API, WebView version, build hash/date and observed result. Unchecked means NOT TESTED, not pass. Do not infer Gemini or Bluetooth support from emulator tests.

## Native original audio
- [ ] Install debug APK; launch cold, verify edge-to-edge contrast and system bars, 48dp controls, TalkBack labels, large font and landscape layouts.
- [ ] Play each original session; verify audible output, metadata and cover. Seek near the end, pause/resume, previous/next; no unwanted auto-play after restart.
- [ ] Queue move/remove/current-item removal/clear; shuffle on/off; repeat off/all/one across track end and queue boundary.
- [ ] Background app and lock screen for at least 10 minutes with repeat-all; use notification, lock-screen and Bluetooth controls. Check service stops when idle and no stale notification remains.
- [ ] Competing audio, transient focus loss, phone call, headphone removal, Bluetooth disconnection/reconnection; verify focus recovery and no unexpected speaker playback.
- [ ] Rotate during playback and seek. Kill background process, reopen; queue and approximate position restore paused. Force-stop must not restart sound. Verify recently cleared queue does not reappear.
- [ ] Reorder a playlist, rename, remove and delete; reopen app. Favourites, followed artist and local search/history remain consistent.

## YouTube account (requires docs/GOOGLE_SETUP.md)
- [ ] Consent allow/deny, cancel and retry, rotation during consent, unavailable Play services, wrong signing SHA-1 and unregistered client.
- [ ] Search videos/channels, no matches, submit rapidly with different terms, load another page, turn network off and retry. Confirm videos open externally and never play in native engine.
- [ ] Existing multi-page playlists and contents. Create a private *test* playlist; add video, duplicate add, existing duplicate entries, remove by item ID, reorder/manual-sort rejection, rename, delete. Verify changes in YouTube.
- [ ] Deleted/private/unavailable videos, account with no channel, token expiry, revoked scope, quota exhaustion and server error. No duplicate writes after retrying an interrupted request.
- [ ] Disconnect while reading/writing; late responses must not repopulate memory. Reconnect after process death. Remove app grant from Google account and check recoverable error.

## Browser experiment
- [ ] Fixture reports at least one blocked request; ordinary HTTPS page navigation/back/reload; JavaScript defaults off and opt-in switch works.
- [ ] HTTP/file/content/javascript/intent URLs, credential-bearing URLs and YouTube domains rejected. TLS failure never bypassed. Camera/microphone/geolocation requests denied.
- [ ] Third-party cookies disabled; first-party cookies persist only in browser; clear-data removes cookies/storage/cache. No OAuth inside WebView.
- [ ] Rotation, open/close 20 times, foreground/background and renderer termination; recover without app crash. Heap dump after GC confirms Activity/WebView not retained. Measure peak Java/native PSS with any future native filter lists; no memory benchmark is claimed now.

## Gemini / assistant (real compatible device required)
- [ ] Document device, Android, Gemini/Assistant version and default assistant, account and language.
- [ ] While original audio is active, ask pause/resume/next/previous/seek; record exact phrases and outcomes separately.
- [ ] Voice play-by-track-name and cold-start media search are NOT IMPLEMENTED. Manifest media-search routing/MediaLibraryService search callback would be required to explore these; a MediaSession alone is insufficient.

## Release gate
- [ ] Execute instrumentation suite on API29 and current API37 device/emulator; validate on at least one physical device.
- [ ] Finish cloud setup and real account testing; native ABI/16KB tests before enabling any Rust filter.
- [ ] Review privacy copy, accessibility, dependency licences and notices; use a separate release signing key kept outside the repository. Release build is not a signed distribution approval.

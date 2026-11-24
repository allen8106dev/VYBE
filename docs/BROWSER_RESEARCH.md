> Historical baseline. The implemented browser/JNI proof and current device results are recorded in [PLAYBACK_FEASIBILITY.md](PLAYBACK_FEASIBILITY.md).

# Browser experiment

Read alongside OPEN_SOURCE_EVALUATION.md. Scope: ordinary HTTPS browsing, isolated from cloud YouTube metadata and native sample playback.

[WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest)) exposes synchronous resource decisions off the UI thread, but omits complete Chromium network control, request bodies, all redirect hops, blob/javascript traffic and a reliable request resource type. Service workers require separate handling. Main-frame origin is not necessarily each iframe initiator. Returning a response is not equivalent to Chromium URLLoader interception. A host-only prototype explicitly does not parse ABP/EasyList syntax.

[AndroidX WebKit](https://developer.android.com/reference/androidx/webkit/WebViewCompat) document-start scripts are feature-gated by the installed WebView. evaluateJavascript at page-finished is too late for many scriptlets and first paint; frames, CSP, isolated worlds, DOM changes and SPAs complicate cosmetics. Untrusted scripts and a broad addJavascriptInterface introduce attack surface. Muse does not expose such a bridge or inject third-party scripts.

Prototype requirements: HTTPS validation on every navigation and resource; disable file/content/mixed content access, release WebView on disposal, pause/resume with Activity lifecycle, cancel TLS failures, recover renderer death by destroying/recreating, block arbitrary intent schemes, no automatic permission grants. Cookies are browser-specific, third-party cookies off; OAuth never runs in the WebView. Browser cookies and API tokens are separate. Diagnostics count intercepted requests without logging URLs, cookies or tokens.

Streaming often uses MSE/blob, service workers and same-origin media/ads; request filtering cannot reliably distinguish these. WebView background execution is subject to renderer/process termination and OS throttling. A foreground service cannot turn it into a durable native audio engine. A DOM media observation bridge would need origin-limited messaging, navigation generation IDs, explicit command acknowledgements, buffering/seek/error/queue mapping and device validation; none is claimed implemented.

Native MediaSession wraps a real ExoPlayer only. Browser pages are not passed off as Media3 Players. Browser-to-native state synchronization and Gemini commands remain unvalidated. Official YouTube results open in YouTube/external browser, where that application owns playback and controls.

[YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies) prohibit relevant player interference, ad suppression, audio isolation and background player behavior. No extraction, hidden player, DRM bypass, or YouTube filtering is implemented. The diagnostics browser excludes YouTube-related domains and does not share a player with the catalogue.

Native wrapper integration is deferred behind the concurrency/ABI gate in the evaluation. The first browser experiment uses original, bounded host rules on a local HTTPS-origin fixture to demonstrate interception; it does not claim real-world adblock coverage. Remote filter downloads, cosmetics, scriptlets, list updates and Brave equivalence are NOT IMPLEMENTED. Validate navigation, TLS, cookies, renderer death, rotation, leak behavior and foreground/background cycles on devices before considering the experiment reliable.

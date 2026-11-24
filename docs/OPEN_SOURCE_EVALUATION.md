> Historical baseline. The implemented browser/JNI proof and current device results are recorded in [PLAYBACK_FEASIBILITY.md](PLAYBACK_FEASIBILITY.md).

# Source evaluation ? 2026-11-23

All four requested repositories were shallow-cloned into `research/`, outside production sources. Network initially failed inside the sandbox; approved git downloads succeeded. Brave Core checkout reported a working-tree checkout failure on Windows; the specific Shields files listed below were present and read successfully. It was not a complete Chromium checkout. This is source inspection, not an upstream build or device benchmark. No upstream implementation was copied into production.

| Source | Inspected commit | Licence |
|---|---|---|
| [adblock-rust](https://github.com/brave/adblock-rust) | 1108c61472dd5a29ae56388ee936048c4fe5a69f | MPL-2.0, LICENSE / Cargo.toml |
| [Orbit](https://github.com/egeeuzun/orbit) | c2b6077f0c309b2a8fa830a7a8c8e19e4cf4259a | No root LICENSE found in inspected checkout; reuse blocked pending clarification |
| [Kotlin wrapper](https://github.com/mlm-games/adblock-rs-kt-wrapper) | f1d317da32c280bcd401e0f86fb606aedfd4c8dc | MPL-2.0, LICENSE |
| [Brave Core](https://github.com/brave/brave-core) | 637ea0f2bbbfbe98cb458518cc716134000f1bdb | MPL-2.0 root, additional third-party licences apply |

## A. Published Kotlin wrapper

Inspected `adblock/src/main/java/org/mlm/adblock/AdblockEngine.kt`, `RequestTypeMapper.kt`, `adblock/src/main/rust/src/lib.rs`, Cargo.toml, module Gradle and gradle.properties. Coordinates at inspection: `io.github.mlm-games:adblock:0.1.3`.

Architecture: Kotlin owns a jlong handle, calls JNI, Rust holds `Mutex<EngineHandle>`. Network decisions and cosmetic resources return JSON. Filter parsing replaces the engine; resources are separately loaded; serialization is exposed. Cargo disables default features and enables embedded-domain-resolver, avoiding upstream single-thread Engine. Dependencies: Rust adblock 0.13.2 range, jni 0.21, anyhow, serde_json; Gradle cargo-ndk task targets four ABIs. Java bytecode target 21 needs app toolchain alignment. Repository README says minSdk26; actual artifact manifest and binaries must be verified before consumption.

Confirmed in source: network matches/exceptions, redirects/rewrites as result fields, URL cosmetics, resources, serialize/deserialize, close. Not confirmed: correctness of prebuilt ABIs, 16KB page alignment, native crash resistance, performance, actual WebView behavior. Library provides no WebView interception or filter updater itself.

Critical risk: AtomicBoolean closed check does not hold ownership through a JNI call. Concurrent close can free Box while a query dereferences it; Rust Mutex alone does not protect handle lifetime. serialize/deserialize also lack consistent closed guards. Wrap *all* operations including close in an external lock, or patch upstream with handle registry/ownership protection. Do not assume advertised thread safety covers destruction. Low-to-medium integration effort after this gate. Recommended future native candidate, pinned and audited, not enabled by default.

## B. Orbit architecture

Inspected `app/.../adblock/{AdblockEngine,NativeAdblock,AdblockService}.kt`, `browser/{OrbitWebViewClient,WebViewFactory,CosmeticBridge}.kt`, `rust/adblock-jni/src/lib.rs` and Cargo.toml.

Architecture: Kotlin ReentrantReadWriteLock protects query versus swap/free; Rust Arc<Engine>; native host fast path, decision cache, background list compilation and serialized engine. AdblockService stamps caches, reloads resources, retains disk lists and uses bounded include depth. WebView request interception skips main frame and allowlisted pages. Cosmetics are injected through document-start support and a page bridge. Dependencies include Android WebView/AndroidX WebKit, Rust JNI, adblock 0.13.5 with embedded resolver, regex handling and CSS validation.

Confirmed: lifecycle locking, interception, engine cache, include handling and separate cosmetic query work in source. Unverified: effectiveness against current websites/video, memory claims in comments, background media reliability and security of all bridge inputs. Large filter text, Rust parser structures and old/new engines coexist during refresh, so peak memory must be measured; comments are not benchmarks. Licence absence prevents copying. Medium-to-high cost to transplant full browser architecture; use the ownership and off-main compilation *ideas*, write independent code. Do not copy Orbit media keepalive tricks into official YouTube playback.

## C. Independent JNI wrapper

Inspected upstream `src/engine.rs`, `src/request.rs`, `src/resources` and Cargo.toml. Current checkout crate 0.13.6, edition 2024. Default `single-thread` disables Send/Sync; explicitly disable it for multithreaded Android use. Engine exposes request matching, cosmetics/resources and serialization; this is not a browser.

Architecture proposal: opaque registry handles or application-level lock held through every FFI call; bounded trusted filters compiled off-main; atomic versioned engine replacement; resources loaded after deserialize; recoverable Kotlin errors, no Rust panic across JNI. Dependencies: NDK, modern Rust edition support, cargo-ndk, JNI crate, adblock transitive dependencies. Need reproducible per-ABI builds, R8 keep rules and 16KB alignment inspection. High initial and continuing maintenance cost. Unverified: all Android runtime behavior, memory/performance/compatibility until built and tested. Reject for initial Muse because it recreates a wrapper without a demonstrated requirement.

## D. Brave Core fork

Inspected `components/brave_shields/core/browser/{ad_block_engine.h,ad_block_service.h,ad_block_dat_cache_manager.cc}`. C++ engine uses Rust cxx bridge, sequence checking, resource providers, DAT cache and Chromium task runners; AdBlockService uses sequence-bound ownership and subscription/component services. This is integration inside Chromium, not a drop-in Android WebView library. Dependencies include Chromium source/build toolchain, GN/Ninja, C++ and Rust bridges, browser/renderer plumbing and updater services.

Confirmed source functionality includes engine resource setup, request checks with origin/type/method, CSP hooks, cosmetics and cache ownership. Not confirmed: a complete Brave build here, parity or Android runtime behavior. Very high disk/build/security-patch and licence inventory cost. Reject fork: no requirement currently justifies replacing Android WebView/Android Studio project with Chromium.

## Recommendation and licence gate

Keep Muse playback independent. Ship an isolated browser experiment with tiny original deterministic host rules and diagnostics. Add A only after artifact and concurrency validation; B informs ownership design without code reuse. C and D are fallback research, not production dependencies. A filtering engine cannot guarantee Brave parity, video-ad removal, hidden playback or stable background pages.

MPL distribution requires preserving notices and providing corresponding covered source/modifications under applicable terms. Filter lists and scriptlet resources have their own licences; do not assume the engine licence covers them. No third-party lists or scriptlets are bundled now. Maintain a licence inventory before integrating native artifacts. Runtime minSdk is 29 in Muse; ABI coverage, 16KB native pages, WebView feature support and memory budgets need API29 plus modern 64-bit device tests. No memory figures have been measured.

## Local native toolchain inspection

`cargo --version` reported 1.99.1; `rustup target list --installed` listed only `x86_64-pc-windows-msvc`. `cargo ndk --version` reported no such command. Android NDK27.0.12077973 is installed. Thus Android Rust targets and cargo-ndk would need installation before a source-built JNI prototype; no native build was attempted and no native build success is claimed.

package com.allen.vybe.browser

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.media.*
import android.media.session.*
import android.media.browse.MediaBrowser
import android.service.media.MediaBrowserService
import android.net.Uri
import android.net.http.SslError
import android.os.*
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import org.json.JSONTokener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** A browser experiment, independent of the YouTube REST client and native Media3 player.
 * No token, javascript interface or media extraction endpoint is used.
 */
// Every posted notification is a MediaStyle notification with a live MediaSession token.
// Android 13 explicitly exempts media-session notifications from POST_NOTIFICATIONS:
// https://developer.android.com/develop/ui/views/notifications/notification-permission#exemptions
@SuppressLint("SetJavaScriptEnabled")
class BrowserPlaybackService : MediaBrowserService() {
    inner class LocalBinder : Binder() { val service get() = this@BrowserPlaybackService }
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val filter = LabFilter()
    private val browserFilter = LabFilter()
    private var filtersReady = false
    private var pendingNavigation = false
    private val blocked = AtomicInteger()
    private val listBlocked = AtomicInteger()
    val filterBlocks get() = listBlocked.get()
    private lateinit var web: WebView
    private lateinit var wrapper: MutableContextWrapper
    private lateinit var session: MediaSession
    val mediaToken get() = session.sessionToken
    private var uiVisible = true
    private var playing = false
    private var lastMetadata = ""
    private var lastNotification = ""
    private var pauseCause = "none"
    private var dead = false
    @Volatile private var page = ""
    private var generation = 0
    private var evaluating = false
    private var evaluationId = 0L
    private var evaluationStarted = 0L
    private val musicQueue = MusicQueue()
    private val queue get() = musicQueue.ids
    private val index get() = musicQueue.index
    private val titles get() = musicQueue.names
    private val artists get() = musicQueue.artists
    private val artistImages get() = musicQueue.artistImages
    private var handledEnd = false
    private var prepared = false
    private var lastQueue = ""
    private var playbackError: String? = null
    private var sleepUntil = 0L
    private val sleepAction = Runnable { sleepUntil=0;command("pause") }
    private var artistImage = ""
    private var queueNames = emptyList<String>()
    private var unmuteOnStart = false
    private var startupUnmuteUntil = 0L
    private var pageTitle = ""
    private var duration = 0L
    private var playerActivity: Class<*> = BrowserPlaybackActivity::class.java
    val snapshot get() = BrowserPlayerState(queue.getOrNull(index).orEmpty(), titles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: pageTitle.ifBlank { "YouTube playback" }, artists.getOrNull(index).orEmpty(), playing && observedAt > 0 && SystemClock.elapsedRealtime() - observedAt <= 5000, lastPosition, duration, index, queue.size, diagnostics, musicQueue.names, artistImage, musicQueue.songs, musicQueue.shuffle, musicQueue.repeat, musicQueue.canNext, sleepUntil, playbackError)
    fun playerActivity(activity: Class<*>) { playerActivity = activity }
    fun select(selected: Int) { if(musicQueue.select(selected)) navigate() }
    fun enqueue(song: QueueSong, next: Boolean = false) {
        val empty=queue.isEmpty();musicQueue.add(song,next)
        if(empty&&queue.isNotEmpty()){session.isActive=true;startForeground(440,notification());navigate()} else publish()
    }
    fun enqueueAll(songs: List<QueueSong>) {
        val empty=queue.isEmpty()
        val known=musicQueue.ids.toMutableSet()
        songs.filter{known.add(it.id)}.forEach { musicQueue.add(it) }
        if(empty&&queue.isNotEmpty()){session.isActive=true;startForeground(440,notification());navigate()} else publish()
    }
    fun move(from: Int,to: Int) { musicQueue.move(from,to);publish() }
    fun remove(at: Int) { val changed=musicQueue.remove(at);if(queue.isEmpty())command("stop") else if(changed)navigate() else publish() }
    fun clearUpcoming() { musicQueue.clearUpcoming();publish() }
    fun sleep(minutes: Int) {
        handler.removeCallbacks(sleepAction);sleepUntil=if(minutes>0)SystemClock.elapsedRealtime()+minutes.coerceAtMost(180)*60000L else 0
        if(sleepUntil>0)handler.postDelayed(sleepAction,sleepUntil-SystemClock.elapsedRealtime());publish()
    }
    fun reportError(message: String?) { playbackError=message;publish() }
    fun prepareSongs(songs: List<QueueSong>) {
        script("m.pause();");updateWake(false);generation++;evaluationId++;evaluating=false
        musicQueue.replace(songs,0);prepared=true;playing=false;lastPosition=0;duration=0;observedAt=SystemClock.elapsedRealtime();publish()
    }
    fun seek(position: Long) { if(duration > 0) script("m.currentTime=${position.coerceIn(0, duration) / 1000.0};") }

    private var pending = ""
    private var pendingAt = 0L
    private var observed = "No HTML media observed"
    private var lastPosition = 0L
    private var observedAt = 0L
    var diagnostics = "Starting browser"; private set
    private var wake: PowerManager.WakeLock? = null
    private var wakeRenewedAt = 0L
    private var health = ""
    fun hostVisible(parent: FrameLayout, visible: Boolean) { if(web.parent === parent) uiVisible = visible }
    private val poll = object : Runnable {
        override fun run() {
            if (dead) return
            if(queue.isEmpty()||prepared) { handler.postDelayed(this, 1000); return }
            if (evaluating && SystemClock.elapsedRealtime() - evaluationStarted > 5000) {
                evaluating = false; playing = false
                observed = "Browser observation timed out; playback state unknown"
                publish()
            }
            if (!evaluating) {
                evaluating = true; evaluationStarted = SystemClock.elapsedRealtime()
                val stamp = generation
                val requestId = ++evaluationId
                web.evaluateJavascript(OBSERVE) { raw ->
                    if (!dead && stamp == generation && requestId == evaluationId) {
                        evaluating = false; observe(raw)
                    }
                }
            }
            handler.postDelayed(this, if(uiVisible) 1000 else 2000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        // Chromium owns the HTML audio sink and its AudioFocusDelegate. A second
        // AudioManager request here competes with that delegate during transitions.
        // The bridge sends transport commands; it does not acquire its own focus.
        session = MediaSession(this, "VYBE browser laboratory").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = command("play")
                override fun onPlayFromSearch(query: String?, extras: Bundle?) { requestSearch(query,extras,true) }
                override fun onPrepareFromSearch(query: String?, extras: Bundle?) { requestSearch(query,extras,false) }
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { musicQueue.songs.indexOfFirst { it.id==mediaId }.takeIf { it>=0 }?.let(::select) }
                override fun onSkipToQueueItem(id: Long) { select(id.toInt()) }
                override fun onFastForward() { seek(lastPosition+10000) }
                override fun onRewind() { seek(lastPosition-10000) }
                override fun onCustomAction(action: String, extras: Bundle?) { if(action in setOf("shuffle","repeat"))command(action) }
                override fun onPause() { pauseCause = "system MediaSession"; command("pause") }
                override fun onSkipToNext() = command("next")
                override fun onSkipToPrevious() = command("previous")
                override fun onStop() = command("stop")
                override fun onSeekTo(pos: Long) { script("m.currentTime=${pos.coerceAtLeast(0) / 1000.0};") }
            }, handler)
            isActive = true
        }
        sessionToken = session.sessionToken
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Music playback", NotificationManager.IMPORTANCE_LOW))
        startForeground(440, notification())
        wrapper = MutableContextWrapper(this)
        // Experimental: keep Chromium's window visibility notification active when the
        // native music surface leaves the foreground. This is not Brave's WebContents patch.
        web = object : WebView(wrapper) {
            override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(android.view.View.VISIBLE) }
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(true) }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, BACKGROUND_SCRIPT, setOf("https://m.youtube.com", "https://www.youtube.com"))
            WebViewCompat.addDocumentStartJavaScript(web, YouTubeBrowserScripts.RESPONSE_FILTER, setOf("https://m.youtube.com", "https://www.youtube.com"))
            WebViewCompat.addDocumentStartJavaScript(web, YouTubeBrowserScripts.STARTUP_AUDIO, setOf("https://m.youtube.com", "https://www.youtube.com"))
            WebViewCompat.addDocumentStartJavaScript(web, BrowserMediaHealth.SCRIPT, setOf("https://m.youtube.com", "https://www.youtube.com"))
        }
        val assetLoader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false; safeBrowsingEnabled = true
            setSupportMultipleWindows(false); setGeolocationEnabled(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !allowed(request.url)
            override fun onPageStarted(view: WebView, url: String, icon: Bitmap?) {
                page = url; generation++; evaluationId++; evaluating = false
                unmuteOnStart = true; startupUnmuteUntil = SystemClock.elapsedRealtime() + 15000
                playing = false; lastPosition = 0; observedAt = 0
                observed = "Loading"; publish()
            }
            override fun onPageFinished(view: WebView, url: String) {
                CookieManager.getInstance().flush()
                if (url == FIXTURE) view.evaluateJavascript(filter.scriptlet(FIXTURE), null)
                else {
                    val stamp = generation
                    worker.execute {
                        val css = browserFilter.css(url)
                        if(css.length in 1..262144) handler.post {
                            if(!dead && generation == stamp) view.evaluateJavascript("(()=>{const s=document.createElement('style');s.textContent=${JSONObject.quote(css)};document.head?.appendChild(s);})()", null)
                        }
                    }
                }
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (!allowed(request.url)) return response("text/plain", "Blocked scheme")
                // Original fixture rules. Community lists filter ordinary browser subresources. No official IFrame API player is loaded.
                if (request.url.host == "tracker.muse.invalid" && filter.blocks(url, "https://muse.invalid/lab", "image")) {
                    blocked.incrementAndGet(); return response("text/plain", "", 403)
                }
                if (url == FIXTURE) return response("text/html", fixture())
                if (request.url.host == "tracker.muse.invalid") return response("image/svg+xml", "<svg xmlns='http://www.w3.org/2000/svg'/>")
                if (request.url.host != "appassets.androidplatform.net" && !request.isForMainFrame) {
                    val destination = request.requestHeaders.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", true) }?.value.orEmpty()
                    val type = when(destination) {
                        "audio", "video" -> "media"
                        "script" -> "script"
                        "style" -> "stylesheet"
                        "image" -> "image"
                        "font" -> "font"
                        "iframe" -> "subdocument"
                        else -> if(request.url.path.orEmpty().contains("videoplayback")) "media" else com.allen.vybe.filter.RustFilterBridge.requestType(request.url, request.requestHeaders.entries.firstOrNull { it.key.equals("Accept", true) }?.value)
                    }
                    if (browserFilter.blocks(url, page, type)) { blocked.incrementAndGet(); listBlocked.incrementAndGet(); return response("text/plain", "", 403) }
                    if(page == FIXTURE && request.url.host == "securepubads.g.doubleclick.net") return response("text/plain", "")
                }
                return assetLoader.shouldInterceptRequest(request.url)
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel(); observed = "TLS rejected"; publish() }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { observed = "WebView error ${error.errorCode}"; publish() }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                dead = true; playing = false; observed = "Renderer exited; close and reopen lab"; publish(); stopSelf(); return true
            }
        }
        worker.execute {
            filter.initialize()
            val loaded = runCatching {
                val rules = listOf("filters/easylist.txt", "filters/easyprivacy.txt").joinToString("\n") { name ->
                    assets.open(name).bufferedReader().use { it.readText() }.also { check(it.length <= 8 * 1024 * 1024) }
                }
                browserFilter.initialize(rules)
                check(browserFilter.status == "Rust JNI ready")
            }.isSuccess
            handler.post {
                if(!dead) {
                    filtersReady = loaded && filter.status == "Rust JNI ready"
                    if(filtersReady && pendingNavigation) navigate()
                    else if(!filtersReady) { observed = "Filtering unavailable; browser navigation withheld"; publish() }
                }
            }
        }
        handler.post(poll)
    }
    private fun allowed(uri: Uri): Boolean = uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443)
    fun attach(parent: FrameLayout, context: Context, interactive: Boolean = true) {
        (web.parent as? ViewGroup)?.removeView(web); wrapper.baseContext = context
        web.setOnTouchListener { _, _ -> !interactive }
        web.importantForAccessibility = if(interactive) android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        parent.addView(web, FrameLayout.LayoutParams(-1, -1)); web.onResume()
    }
    fun detach(parent: FrameLayout) { if(web.parent === parent) { parent.removeView(web); wrapper.baseContext = this } }
    fun load(items: List<String>, selected: Int, names: List<String> = emptyList(), creators: List<String> = emptyList(), creatorImages: List<String> = emptyList()) {
        val valid = items.filter { it == "fixture" || it.matches(Regex("[A-Za-z0-9_-]{11}")) }.take(100)
        if (valid.isEmpty()) { observed = "Invalid video queue"; publish(); return }
        session.isActive = true; startForeground(440, notification())
        musicQueue.replace(valid.mapIndexed { n,id -> QueueSong(id,names.getOrNull(n)?.take(200).orEmpty().ifBlank { "YouTube song ${n+1}" },creators.getOrNull(n)?.take(150).orEmpty(),creatorImages.getOrNull(n)?.take(2048).orEmpty()) },selected)
        playbackError=null;prepared=false;navigate()
    }
    private fun navigate() {
        if (dead || queue.isEmpty()) return
        if (!filtersReady) { pendingNavigation = true; observed = "Preparing enforced browser filters"; publish(); return }
        pendingNavigation = false
        handledEnd=false;prepared=false;playbackError=null
        pending = ""; playing = false
        // Invalidate immediately: onPageStarted arrives later, after callers can inspect state.
        generation++; evaluationId++; evaluating = false; observedAt = 0; lastPosition = 0
        pageTitle = ""; duration = 0; artistImage = artistImages.getOrNull(index).orEmpty(); unmuteOnStart = true
        observed = "Loading"; publish()
        val id = queue[index]
        web.loadUrl(if (id == "fixture") FIXTURE else "https://m.youtube.com/watch?v=$id&autoplay=1")
    }
    fun command(command: String) {
        if (dead) return
        when(command) {
            "stop" -> {
                script("m.pause();"); web.stopLoading(); musicQueue.clear(); playing = false; pending = ""; pendingNavigation = false
                handler.removeCallbacks(sleepAction);sleepUntil=0
                unmuteOnStart = false; generation++; evaluationId++; evaluating = false; observedAt = 0; lastPosition = 0; duration = 0
                updateWake(false)
                queueNames = emptyList()
                observed = "Stopped"; publish(); session.isActive = false
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
            }
            "next" -> { if (musicQueue.next()) navigate() else { observed = "End of queue"; publish() }; return }
            "previous" -> { if(lastPosition>3000)seek(0) else if(musicQueue.previous())navigate();return }
            "shuffle" -> { musicQueue.toggleShuffle();publish();return }
            "repeat" -> { musicQueue.setRepeat((musicQueue.repeat+1)%3);publish();return }
            "play" -> {
                if(queue.isEmpty()){reportError("Choose a song in VYBE to start listening.");return}
                session.isActive=true;startForeground(440,notification())
                if(prepared){navigate();return}
                script("m.muted=false;m.volume=1;m.play().catch(()=>{});")
            }
            "pause" -> { script("m.pause();"); updateWake(false) }
            else -> return
        }
        pending = command; pendingAt = SystemClock.elapsedRealtime(); publish()
    }
    private fun requestSearch(query: String?,extras: Bundle?,play: Boolean) {
        val term=query?.trim()?.take(200).orEmpty().ifBlank {
            listOf(extras?.getString("android.intent.extra.title"),extras?.getString("android.intent.extra.artist")).filterNotNull().joinToString(" ").take(200)
        }
        if(term.isBlank()){if(play)command("play");return}
        searchRequest?.invoke(this,term,play) ?: reportError("Open VYBE to connect your music account.")
    }
    private fun script(body: String) {
        // Fixed commands only; no data from pages or search titles is interpolated.
        web.evaluateJavascript("(()=>{const m=(document.querySelector('video.html5-main-video')||document.querySelector('video,audio'));if(m){$body}})()", null)
    }
    private fun observe(raw: String) {
        observedAt = SystemClock.elapsedRealtime()
        try {
            val value = JSONTokener(raw).nextValue()
            val data = JSONObject(value as? String ?: "{}")
            val present = data.optBoolean("present")
            val avatar = data.optString("avatar")
            if (Uri.parse(avatar).let { it.scheme == "https" && it.host == "yt3.ggpht.com" || it.scheme == "https" && it.host == "yt3.googleusercontent.com" }) artistImage = avatar
            if (present && unmuteOnStart && data.optInt("ready") >= 2 && !data.optBoolean("paused", true)) {
                if(data.optBoolean("muted") || data.optDouble("volume") == 0.0) script("m.muted=false;m.volume=1;")
                else if(SystemClock.elapsedRealtime() > startupUnmuteUntil) unmuteOnStart = false
            }
            pageTitle = data.optString("title").take(200)
            duration = (data.optDouble("duration", 0.0).takeIf { it.isFinite() && it > 0 }?.times(1000)?.toLong() ?: 0).coerceAtMost(24 * 60 * 60 * 1000L)
            playing = present && !data.optBoolean("paused", true) && !data.optBoolean("ended") && data.optInt("ready") >= 2
            lastPosition = (data.optDouble("position", 0.0) * 1000).toLong().coerceAtLeast(0)
            observed = if (!present) "No HTML media observed; page may require a tap/sign-in" else "${if(playing) "Playing" else "Paused/buffering"} ? ${lastPosition / 1000}s ? ready ${data.optInt("ready")} ? muted ${data.optBoolean("muted")} volume ${data.optDouble("volume")} ? response ad fields ${data.optInt("adsRemoved")}"
            if (pending.isNotEmpty()) {
                if ((pending == "play" && playing) || (pending == "pause" && !playing && present)) { observed += " ? $pending acknowledged"; pending = "" }
                else if (SystemClock.elapsedRealtime() - pendingAt > 5000) { observed += " ? $pending unconfirmed"; pending = "" }
            }
            // Buffer starvation lowers readyState without being a user Pause. Keep
            // the CPU awake during refill, and renew the bounded lease for long songs.
            updateWake(present && !data.optBoolean("paused", true) && !data.optBoolean("ended") && data.optInt("error") == 0)
            data.optJSONObject("health")?.let { h -> health = "buffer ${h.optDouble("bufferAhead")}s; waits ${h.optInt("waiting")}/${h.optLong("waitMs")}ms; pauses ${h.optInt("pauses")}; video ${h.optInt("width")}x${h.optInt("height")}; dropped ${h.optInt("dropped")}/${h.optInt("frames")}" }
            publish()
            if(playing)musicQueue.current?.let { songObserved?.invoke(it) }
            if(data.optBoolean("ended")&&!handledEnd){
                handledEnd=true
                if(musicQueue.repeat==2){script("m.currentTime=0;m.play().catch(()=>{});")}
                else if(musicQueue.next(true))navigate()
            } else if(!data.optBoolean("ended"))handledEnd=false
        } catch (_: Exception) { observed = "Browser observation unavailable"; publish() }
    }
    private fun updateWake(active: Boolean) {
        if(!active) { wake?.let { if(it.isHeld) it.release() }; wake = null; wakeRenewedAt = 0; return }
        val now = SystemClock.elapsedRealtime()
        if(wake == null) wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vybe:browser").apply { setReferenceCounted(false) }
        if(wake?.isHeld != true || now - wakeRenewedAt >= 5 * 60 * 1000) {
            wake?.acquire(10 * 60 * 1000L); wakeRenewedAt = now
        }
    }
    private fun publish() {
        if (!::session.isInitialized) return
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or
            (if (musicQueue.canNext) PlaybackState.ACTION_SKIP_TO_NEXT else 0L) or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_PLAY_FROM_SEARCH or PlaybackState.ACTION_PREPARE_FROM_SEARCH or PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
        val fresh = observedAt > 0 && SystemClock.elapsedRealtime() - observedAt <= 5000
        val state = if (!fresh) PlaybackState.STATE_NONE else if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val builder=PlaybackState.Builder().setActions(actions).setActiveQueueItemId(index.toLong()).setState(if(playbackError!=null)PlaybackState.STATE_ERROR else state,lastPosition,if(playing&&fresh)1f else 0f)
        playbackError?.let { builder.setErrorMessage(it) }
        builder.addCustomAction("shuffle",if(musicQueue.shuffle)"Disable shuffle" else "Enable shuffle",android.R.drawable.ic_menu_sort_by_size)
        builder.addCustomAction("repeat","Change repeat",android.R.drawable.ic_menu_rotate)
        session.setPlaybackState(builder.build())
        val metadata = "${snapshot.title}|${snapshot.artist}|$duration"
        if(metadata != lastMetadata) { lastMetadata = metadata; session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,snapshot.title).putString(MediaMetadata.METADATA_KEY_ARTIST,snapshot.artist).putString(MediaMetadata.METADATA_KEY_MEDIA_ID,snapshot.videoId).putLong(MediaMetadata.METADATA_KEY_DURATION,duration).build()) }
        val queueKey=musicQueue.songs.joinToString("|") { "${it.id}:${it.title}" }
        if(queueKey!=lastQueue){lastQueue=queueKey;session.setQueue(musicQueue.songs.mapIndexed { n,s -> MediaSession.QueueItem(MediaDescription.Builder().setMediaId(s.id).setTitle(s.title).setSubtitle(s.artist).build(),n.toLong()) });session.setQueueTitle("VYBE queue")}
        diagnostics = "$observed${if(pending.isEmpty()) "" else " ? awaiting $pending"}\n${filter.status} ? ${if(filtersReady) "EasyList + EasyPrivacy enforced" else "preparing filters"} ? blocked ${blocked.get()} (${listBlocked.get()} list matches) ? ${Uri.parse(page).host.orEmpty()} ? last pause $pauseCause ? focus owner Chromium\n$health"
        mutablePlayer.value = snapshot
        val notificationKey = "$metadata|$state"
        if(notificationKey != lastNotification) { lastNotification = notificationKey; getSystemService(NotificationManager::class.java).notify(440, notification()) }
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, playerActivity), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(snapshot.title)
            .setContentText(snapshot.artist.ifBlank { "YouTube" }).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(playing)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0,1,2))
        listOf("previous", if(playing) "pause" else "play", "next").forEachIndexed { n, action ->
            val pi = PendingIntent.getService(this, n + 1, Intent(this, BrowserPlaybackService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(Notification.Action.Builder(if(action=="pause") android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, action, pi).build())
        }
        return builder.build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { command(it) }; return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (dead) {
            playing = false; musicQueue.clear(); pending = ""; pendingNavigation = false
            handler.removeCallbacks(sleepAction); sleepUntil = 0; updateWake(false)
            session.isActive = false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            mutablePlayer.value = BrowserPlayerState()
        } else command("stop")
        super.onTaskRemoved(rootIntent)
        taskRemoved?.invoke(this)
    }
    override fun onBind(intent: Intent): IBinder? = if(intent.action==SERVICE_INTERFACE)super.onBind(intent) else binder
    override fun onGetRoot(clientPackageName: String,clientUid: Int,rootHints: Bundle?): BrowserRoot? {
        val verified=packageManager.getPackagesForUid(clientUid)?.contains(clientPackageName)==true
        val trusted=getSystemService(MediaSessionManager::class.java).isTrustedForMediaControl(MediaSessionManager.RemoteUserInfo(clientPackageName,-1,clientUid))
        return if(verified&&(clientPackageName==packageName||trusted||clientPackageName=="com.google.android.googlequicksearchbox"))BrowserRoot("queue",null) else null
    }
    override fun onLoadChildren(parentId: String,result: Result<MutableList<MediaBrowser.MediaItem>>) {
        result.sendResult(if(parentId=="queue")musicQueue.songs.map { s -> MediaBrowser.MediaItem(MediaDescription.Builder().setMediaId(s.id).setTitle(s.title).setSubtitle(s.artist).build(),MediaBrowser.MediaItem.FLAG_PLAYABLE) }.toMutableList() else mutableListOf())
    }
    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>) {
        writer.println("VYBE browser proof: playing=$playing positionMs=$lastPosition observedAtMs=$observedAt queueIndex=$index queueSize=${queue.size}")
        writer.println(diagnostics)
    }
    override fun onDestroy() {
        dead = true; mutablePlayer.value = BrowserPlayerState(); handler.removeCallbacksAndMessages(null)
        updateWake(false)
        session.isActive = false; session.release()
        (web.parent as? ViewGroup)?.removeView(web); web.stopLoading(); web.destroy()
        worker.execute { filter.close(); browserFilter.close() }; worker.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    private fun response(mime: String, body: String, code: Int = 200) = WebResourceResponse(mime, "UTF-8", code, if(code==200) "OK" else "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(body.toByteArray()))
    private fun fixture(): String = """<!doctype html><html><meta name="viewport" content="width=device-width,initial-scale=1"><style>${filter.css(FIXTURE)}</style><body><h2>Browser media fixture</h2><p>Original WAV through HTML audio, separate from native Media3. Press Play below.</p><audio controls loop src="https://appassets.androidplatform.net/assets/dawn.wav"></audio><div class="lab-ad">COSMETIC FILTER FAILED if visible</div><img src="https://tracker.muse.invalid/blocked.png"><img src="https://tracker.muse.invalid/allowed.png"><script src="https://securepubads.g.doubleclick.net/gampad/ads?vybe_lab=1"></script><p id="scriptlet">Scriptlets are not loaded.</p></body></html>"""
    companion object {
        private val mutablePlayer = MutableStateFlow(BrowserPlayerState())
        val state = mutablePlayer.asStateFlow()
        val latest get() = state.value
        var searchRequest: ((BrowserPlaybackService,String,Boolean)->Unit)? = null
        var songObserved: ((QueueSong)->Unit)? = null
        var taskRemoved: ((BrowserPlaybackService)->Unit)? = null
        const val FIXTURE = "https://muse.invalid/lab"
        private const val CHANNEL = "browser_playback_lab"
        private const val OBSERVE = """(()=>{const m=(document.querySelector('video.html5-main-video')||document.querySelector('video,audio'));const a=document.querySelector('ytm-slim-owner-renderer img,ytm-video-owner-renderer img,#owner img');return JSON.stringify(m?{present:true,paused:m.paused,muted:m.muted,volume:m.volume,ended:m.ended,ready:m.readyState,position:Number.isFinite(m.currentTime)?m.currentTime:0,duration:Number.isFinite(m.duration)?m.duration:0,title:document.title,avatar:a?.src||'',adsRemoved:window.__vybeAdsRemoved||0,error:m.error?.code||0,health:window.__vybeHealth?.read(m)}:{present:false,title:document.title});})()"""
        // Only the browser lab page receives this script; the official API player is unused.
        private const val BACKGROUND_SCRIPT = """(()=>{for(const [k,v] of [['hidden',false],['visibilityState','visible'],['webkitHidden',false],['webkitVisibilityState','visible']]){try{Object.defineProperty(document,k,{get:()=>v,configurable:true})}catch(e){}}for(const t of ['visibilitychange','webkitvisibilitychange'])document.addEventListener(t,e=>e.stopImmediatePropagation(),true);})()"""
    }
}

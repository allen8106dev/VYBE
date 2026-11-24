package com.allen.vybe.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import android.widget.FrameLayout
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/** Main-thread ownership. Interception uses only immutable policies and atomic counters. */
@SuppressLint("SetJavaScriptEnabled")
class BrowserSession(context: Context, private val report: (BrowserDiagnostics) -> Unit, private val rendererLost: () -> Unit) : FrameLayout(context) {
    private val requests = AtomicInteger(); private val blocked = AtomicInteger()
    private var closed = false
    private var current = "https://muse.invalid/lab"
    private val web = WebView(context)
    init {
        addView(web, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        web.settings.apply {
            javaScriptEnabled = false; domStorageEnabled = false
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false); safeBrowsingEnabled = true
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val allowed = BrowserPolicy.allows(request.url.toString())
                if (!allowed) emit("Navigation blocked: use HTTPS. For YouTube, open the separate browser playback test.")
                return !allowed
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                requests.incrementAndGet()
                val url = request.url.toString()
                if (!BrowserPolicy.allows(url) || BrowserPolicy.blocks(request.url.host)) {
                    blocked.incrementAndGet(); post { emit("Resource blocked by laboratory policy") }
                    return response("text/plain", "", 403, "Blocked")
                }
                if (url == "https://muse.invalid/lab") return response("text/html", FIXTURE)
                if (request.url.host == "muse.invalid") return response("text/plain", "Muse fixture resource")
                return null
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { current = url; emit("Loading") }
            override fun onPageFinished(view: WebView, url: String) { CookieManager.getInstance().flush(); emit("Loaded") }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) emit("Page failed (${error.errorCode}). Check the address and retry.")
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, error: WebResourceResponse) {
                if (request.isForMainFrame) emit("HTTP ${error.statusCode}. Retry or choose another page.")
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel(); emit("Certificate rejected") }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                emit("Renderer exited. Recreating the browser."); close(); rendererLost(); return true
            }
        }
        navigate(current)
    }
    private fun emit(status: String) { if (!closed) report(BrowserDiagnostics(requests.get(), blocked.get(), status, current)) }
    fun navigate(url: String) { if (closed) return; if (BrowserPolicy.allows(url)) web.loadUrl(url) else emit("Use an HTTPS page. YouTube and Google login are excluded from this experiment.") }
    fun javascript(enabled: Boolean) { if (!closed) { web.settings.javaScriptEnabled = enabled; web.settings.domStorageEnabled = enabled; web.reload() } }
    fun back() { if (!closed && web.canGoBack()) web.goBack() }
    fun reload() { if (!closed) web.reload() }
    fun resume() { if (!closed) web.onResume() }
    fun pause() { if (!closed) { web.onPause(); CookieManager.getInstance().flush() } }
    fun clearData() {
        if (closed) return
        CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData(); web.clearCache(true); web.clearHistory()
        emit("Browser cookies, history and cache cleared")
    }
    fun close() {
        if (closed) return
        closed = true; web.stopLoading(); web.webChromeClient = null; web.webViewClient = WebViewClient()
        removeView(web); web.destroy()
    }
    private fun response(mime: String, text: String, status: Int = 200, reason: String = "OK") = WebResourceResponse(mime, "UTF-8", status, reason, mapOf("Cache-Control" to "no-store", "Content-Security-Policy" to "default-src 'none'; img-src https://tracker.muse.invalid; style-src 'unsafe-inline'"), ByteArrayInputStream(text.toByteArray()))
    companion object {
        private const val FIXTURE = """<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='background:#101814;color:#f0fff6;font:18px sans-serif;padding:28px'><p style='color:#6ce4ad'>MUSE / BROWSER LAB</p><h1>A small, honest experiment.</h1><p>This local fixture requests an image from tracker.muse.invalid. The original host rule blocks it. See the counter above.</p><img alt='Blocked test image' src='https://tracker.muse.invalid/pixel.png'><p>This generic fixture uses its original host rule. Open the separate browser media proof to test Rust filtering and media controls.</p><p>Navigate to an HTTPS page to inspect WebView behavior. JavaScript starts disabled.</p></body></html>"""
    }
}

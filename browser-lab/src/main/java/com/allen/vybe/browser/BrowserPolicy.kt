package com.allen.vybe.browser

import java.net.URI
import java.util.Locale

object BrowserPolicy {
    private val excluded = setOf("youtube.com", "youtu.be", "youtube-nocookie.com", "googlevideo.com", "ytimg.com", "accounts.google.com")
    fun allows(url: String): Boolean = runCatching {
        val uri = URI(url); val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        uri.scheme.equals("https", true) && uri.userInfo == null && (uri.port == -1 || uri.port == 443) && excluded.none { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)
    fun blocks(host: String?) = host?.lowercase(Locale.ROOT)?.let { it == "tracker.muse.invalid" || it.endsWith(".tracker.muse.invalid") } ?: false
}
data class BrowserDiagnostics(val requests: Int = 0, val blocked: Int = 0, val status: String = "Ready", val url: String = "https://muse.invalid/lab")

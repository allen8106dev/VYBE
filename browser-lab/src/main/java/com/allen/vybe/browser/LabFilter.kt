package com.allen.vybe.browser

import com.allen.vybe.filter.RustFilterBridge



/** Every JNI operation and destruction shares one lock, protecting the native handle lifetime. */
class LabFilter : AutoCloseable {
    private val lock = Any()
    private var engine: RustFilterBridge? = null
    private data class RequestKey(val url: String, val source: String, val type: String)
    private val decisions = android.util.LruCache<RequestKey, Boolean>(256)
    @Volatile var status = "Not initialized"; private set
    fun initialize(rules: String = "||tracker.muse.invalid^\n@@||tracker.muse.invalid/allowed.png\nmuse.invalid##.lab-ad\nmuse.invalid##+js(lab-marker)") = synchronized(lock) {
        try {
            decisions.evictAll(); engine?.close(); engine = null
            engine = RustFilterBridge().also {
                check(it.loadFilterList(rules))
            }
            val content = android.util.Base64.encodeToString("document.documentElement.dataset.labScriptlet='passed';".toByteArray(), android.util.Base64.NO_WRAP)
            check(engine!!.resources("""[{"name":"lab-marker.js","kind":{"mime":"application/javascript"},"content":"$content"}]"""))
            status = "Rust JNI ready"
        } catch (e: LinkageError) { status = "Native engine unavailable: ${e.javaClass.simpleName}" }
          catch (e: Exception) { status = "Filter initialization failed: ${e.javaClass.simpleName}" }
    }
    fun blocks(url: String, source: String, type: String): Boolean = synchronized(lock) {
        val key = RequestKey(url, source, type)
        decisions.get(key)?.let { return@synchronized it }
        val active = engine ?: return@synchronized false
        val result = active.blocks(url, source, type)
        if(url.length <= 4096 && source.length <= 4096) decisions.put(key, result)
        result
    }
    fun css(url: String): String = synchronized(lock) { engine?.css(url).orEmpty() }
    fun scriptlet(url: String): String = synchronized(lock) { engine?.scriptlet(url).orEmpty() }
    override fun close() = synchronized(lock) { decisions.evictAll(); engine?.close(); engine = null }
}

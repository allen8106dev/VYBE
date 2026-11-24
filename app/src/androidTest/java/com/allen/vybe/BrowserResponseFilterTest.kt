package com.allen.vybe

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.YouTubeBrowserScripts
import com.allen.vybe.lyrics.AutomaticLyrics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BrowserResponseFilterTest {
    @Test fun startupAudioHandlesMutedPlayAndDelayedVolumeRestore() {
        val ins = InstrumentationRegistry.getInstrumentation(); val ready = CountDownLatch(1); val done = CountDownLatch(1)
        lateinit var web: WebView; var result = ""
        ins.runOnMainSync {
            web = WebView(ins.targetContext); web.settings.javaScriptEnabled = true
            web.webViewClient = object : WebViewClient() { override fun onPageFinished(view: WebView, url: String) { ready.countDown() } }
            web.loadDataWithBaseURL("https://muse.invalid/audio-test", "<html><body>Audio state fixture</body></html>", "text/html", "UTF-8", null)
        }
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            ins.runOnMainSync {
                web.evaluateJavascript(YouTubeBrowserScripts.STARTUP_AUDIO + ";(()=>{const m=document.createElement('video');document.body.appendChild(m);m.muted=true;m.defaultMuted=true;m.volume=0;m.dispatchEvent(new Event('play'));const first=!m.muted&&m.volume===1&&!m.defaultMuted;m.muted=true;m.volume=0;m.dispatchEvent(new Event('volumechange'));return first&&!m.muted&&m.volume===1})()") { result = it; done.countDown() }
            }
            assertTrue(done.await(10, TimeUnit.SECONDS)); assertEquals("true", result)
        } finally { ins.runOnMainSync { web.destroy() } }
    }
    @Test fun browserResponseFilterPreservesContentAndUnrelatedJson() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val loaded = CountDownLatch(1); val evaluated = CountDownLatch(1)
        lateinit var web: WebView
        var result = ""
        ins.runOnMainSync {
            web = WebView(ins.targetContext)
            web.settings.javaScriptEnabled = true
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { loaded.countDown() }
            }
            web.loadDataWithBaseURL("https://muse.invalid/response-test", "<html><body>Response fixture</body></html>", "text/html", "UTF-8", null)
        }
        try {
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
            ins.runOnMainSync {
                web.evaluateJavascript(YouTubeBrowserScripts.RESPONSE_FILTER + ";JSON.stringify((()=>{const p=JSON.parse('{\"videoDetails\":{\"videoId\":\"fixture\"},\"playabilityStatus\":{\"status\":\"OK\"},\"streamingData\":{\"formats\":[1]},\"adPlacements\":[1],\"playerAds\":[2],\"adSlots\":[3]}');const u=JSON.parse('{\"adPlacements\":[1]}');window.ytInitialPlayerResponse={videoDetails:{videoId:'fixture'},playabilityStatus:{status:'OK'},playerAds:[1]};return !p.adPlacements&&!p.playerAds&&!p.adSlots&&p.streamingData.formats[0]===1&&u.adPlacements[0]===1&&!window.ytInitialPlayerResponse.playerAds&&window.__vybeAdsRemoved===4})())") { result = it; evaluated.countDown() }
            }
            assertTrue(evaluated.await(10, TimeUnit.SECONDS)); assertEquals("true", org.json.JSONTokener(result).nextValue())
        } finally { ins.runOnMainSync { web.destroy() } }
    }
    @Test fun automaticLyricsFetchesMatchingRealRecordingWithoutImport() = runBlocking {
        // Live provider smoke test, explicitly separate from offline unit tests.
        val result = AutomaticLyrics.find("I Want to Live", "Borislav Slavov", 233000)
        assertTrue("No matching LRCLIB record: ${result.message}", result.recordId > 0)
        assertTrue("Provider returned no lyrics", result.lines.isNotEmpty() || result.plain.isNotBlank())
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "automatic-lyrics-proof.txt").writeText("provider=LRCLIB recordId=${result.recordId} syncedLines=${result.lines.size} plainCharacters=${result.plain.length}")
    }
}

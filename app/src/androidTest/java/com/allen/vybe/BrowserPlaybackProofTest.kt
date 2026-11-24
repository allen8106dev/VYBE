package com.allen.vybe

import android.content.*
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserPlaybackProofTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    @Before fun openLauncher() {
        ins.uiAutomation.executeShellCommand("am start -W -n com.allen.vybe/.MainActivity").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
    private fun await(label: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 15000
        while(System.currentTimeMillis() < end) {
            var ok = false; ins.runOnMainSync { ok = condition() }; if(ok) return
            Thread.sleep(200)
        }
        fail(label)
    }
    @Test fun rustJniNetworkExceptionsCosmeticsAndScriptlets() {
        LabFilter().use { filter ->
            filter.initialize()
            assertEquals("Rust JNI ready", filter.status)
            assertTrue(filter.blocks("https://tracker.muse.invalid/blocked.png", "https://muse.invalid/lab", "image"))
            assertFalse(filter.blocks("https://tracker.muse.invalid/allowed.png", "https://muse.invalid/lab", "image"))
            assertFalse(filter.blocks("https://example.com/image.png", "https://muse.invalid/lab", "image"))
            assertTrue(filter.css("https://muse.invalid/lab").contains(".lab-ad"))
            assertTrue(filter.scriptlet("https://muse.invalid/lab").contains("labScriptlet"))
        }
    }
    @Test fun filterDecisionCachePreservesTypeSourceAndRuleReload() {
        LabFilter().use { filter ->
            filter.initialize("||tracker.muse.invalid^\$image,domain=muse.invalid")
            val url = "https://tracker.muse.invalid/image.png"
            assertTrue(filter.blocks(url, "https://muse.invalid/lab", "image"))
            assertTrue(filter.blocks(url, "https://muse.invalid/lab", "image"))
            assertFalse(filter.blocks(url, "https://other.invalid/", "image"))
            assertFalse(filter.blocks(url, "https://muse.invalid/lab", "script"))
            filter.initialize("@@||tracker.muse.invalid^")
            assertFalse(filter.blocks(url, "https://muse.invalid/lab", "image"))
        }
    }
    @Test fun htmlAudioSessionCommandsAndBackground() {
        val ctx = ins.targetContext
        val intent = Intent(ctx, BrowserPlaybackActivity::class.java).putStringArrayListExtra("queue", arrayListOf("fixture", "fixture"))
        var service: BrowserPlaybackService? = null
        val bound = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { service = (binder as BrowserPlaybackService.LocalBinder).service; bound.countDown() }
            override fun onServiceDisconnected(name: ComponentName) { service = null }
        }
        ActivityScenario.launch<BrowserPlaybackActivity>(intent).use { scenario ->
            ctx.bindService(Intent(ctx, BrowserPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
            assertTrue(bound.await(10, TimeUnit.SECONDS))
            try {
                await("HTML media failed to load: ${service?.diagnostics}") { service?.diagnostics?.contains("ready 4") == true }
                val dom = CountDownLatch(1)
                var filterDom = ""
                scenario.onActivity { activity -> findWeb(activity.window.decorView)!!.evaluateJavascript("JSON.stringify({hidden:getComputedStyle(document.querySelector('.lab-ad')).display==='none',scriptlet:document.documentElement.dataset.labScriptlet})") { filterDom = it; dom.countDown() } }
                assertTrue(dom.await(5, TimeUnit.SECONDS))
                assertTrue("CSS did not hide fixture: $filterDom", filterDom.contains("true"))
                assertTrue("Scriptlet did not execute: $filterDom", filterDom.contains("passed"))
                ins.runOnMainSync { assertTrue("Bundled lists did not block the controlled advertising request", service!!.filterBlocks > 0) }
                lateinit var controller: MediaController
                ins.runOnMainSync { controller = MediaController(ctx, service!!.mediaToken) }
                // An application media command, not a forged WebView input event.
                ins.runOnMainSync { controller.transportControls.play() }
                await("HTML media did not play: ${service?.diagnostics}") { controller.playbackState?.state == PlaybackState.STATE_PLAYING }
                ins.runOnMainSync { controller.transportControls.pause() }
                await("Browser pause unacknowledged") { controller.playbackState?.state == PlaybackState.STATE_PAUSED }
                ins.runOnMainSync { controller.transportControls.skipToNext() }
                await("Browser next did not navigate") { controller.metadata?.getString("android.media.metadata.TITLE")?.contains("2/2") == true }
                await("Next HTML media not ready") { service?.diagnostics?.contains("ready 4") == true }
                ins.runOnMainSync { controller.transportControls.skipToPrevious() }
                await("Browser previous did not navigate") { controller.metadata?.getString("android.media.metadata.TITLE")?.contains("1/2") == true }
                await("Previous HTML media not ready") { service?.diagnostics?.contains("ready 4") == true }
                ins.runOnMainSync { controller.transportControls.skipToNext(); controller.transportControls.skipToPrevious() }
                await("Rapid navigation lost the browser observer") { service?.diagnostics?.contains("ready 4") == true && controller.metadata?.getString("android.media.metadata.TITLE")?.contains("1/2") == true }
                ins.runOnMainSync { controller.transportControls.play() }
                await("Browser resume failed") { controller.playbackState?.state == PlaybackState.STATE_PLAYING }
                var before = 0L
                ins.runOnMainSync { before = controller.playbackState?.position ?: 0 }
                shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME")
                Thread.sleep(6000)
                await("Browser stopped after Home") { controller.playbackState?.state == PlaybackState.STATE_PLAYING && (controller.playbackState?.position ?: 0) > before + 3000 }
                ins.runOnMainSync { before = controller.playbackState?.position ?: 0 }
                shell("am start -W -a android.settings.SETTINGS")
                Thread.sleep(6000)
                await("Browser stopped in another app") { controller.playbackState?.state == PlaybackState.STATE_PLAYING && (controller.playbackState?.position ?: 0) > before + 3000 }
                ins.runOnMainSync {
                    val notification = ctx.getSystemService(android.app.NotificationManager::class.java).activeNotifications.first { it.id == 440 }.notification
                    notification.actions[1].actionIntent.send()
                }
                await("Background notification pause failed") { controller.playbackState?.state == PlaybackState.STATE_PAUSED }
            } finally {
                openLauncher()
                ins.runOnMainSync { service?.command("stop") }; ctx.unbindService(connection)
            }
        }
    }
    @Test fun youtubePageLoadsInsideAppForManualPlayback() {
        val ctx = ins.targetContext
        // Public Google-owned player demo video. No API credentials or external intent.
        val id = InstrumentationRegistry.getArguments().getString("videoId") ?: "M7lc1UVf-VE"
        ActivityScenario.launch<BrowserPlaybackActivity>(Intent(ctx, BrowserPlaybackActivity::class.java).putStringArrayListExtra("queue", arrayListOf(id, id))).use { scenario ->
            Thread.sleep(18000)
            var web: WebView? = null
            scenario.onActivity { activity -> web = findWeb(activity.window.decorView) }
            assertNotNull("No in-app WebView", web)
            ins.runOnMainSync {
                assertTrue("YouTube navigation did not remain inside WebView", web!!.url.orEmpty().startsWith("https://m.youtube.com/"))
                web!!.evaluateJavascript("(()=>{const m=document.querySelector('video,audio');if(m)m.play().catch(()=>{});})()", null)
            }
            Thread.sleep(8000)
            val probe = CountDownLatch(1)
            ins.runOnMainSync {
                web!!.evaluateJavascript("JSON.stringify({host:location.host,media:!!document.querySelector('video'),paused:document.querySelector('video')?.paused,ready:document.querySelector('video')?.readyState,position:document.querySelector('video')?.currentTime,unavailable:document.body.innerText.includes('Video unavailable'),botCheck:document.body.innerText.includes('confirm you?re not a bot')})") { result ->
                    java.io.File(ctx.filesDir, "youtube-browser-probe.json").writeText(result); probe.countDown()
                }
            }
            assertTrue(probe.await(5, TimeUnit.SECONDS))
        }
    }
    private fun shell(command: String) {
        ins.uiAutomation.executeShellCommand(command).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
    }
    private fun findWeb(v: View): WebView? {
        if(v is WebView) return v
        if(v is ViewGroup) for(i in 0 until v.childCount) findWeb(v.getChildAt(i))?.let { return it }
        return null
    }
}

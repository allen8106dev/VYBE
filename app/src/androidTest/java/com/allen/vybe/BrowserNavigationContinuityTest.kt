package com.allen.vybe

import android.content.*
import android.os.IBinder
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.BrowserPlaybackService
import com.allen.vybe.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real YouTube through the same retained host and native navigation used by song taps. */
class BrowserNavigationContinuityTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun realYoutubeSurvivesNativePlayerHomeSearchAndLibrary() {
        val ins = InstrumentationRegistry.getInstrumentation(); val context = ins.targetContext
        fun shell(cmd: String) = ins.uiAutomation.executeShellCommand(cmd).use { p -> java.io.FileInputStream(p.fileDescriptor).use { String(it.readBytes()) } }
        shell("am start -W -f 0x10008000 -n com.allen.vybe/.MainActivity")
        val results = JSONObject(); var engine: BrowserPlaybackService? = null
        val frameIntervals = java.util.concurrent.CopyOnWriteArrayList<Long>()
        val stacks = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val sampler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        var recording = false; var lastFrame = 0L
        val frameProbe = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(time: Long) {
                if(!recording) return
                if(lastFrame > 0) frameIntervals.add((time - lastFrame) / 1_000_000)
                lastFrame = time; android.view.Choreographer.getInstance().postFrameCallback(this)
            }
        }
        val bound = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(n: ComponentName, b: IBinder) { engine = (b as BrowserPlaybackService.LocalBinder).service; bound.countDown() }
            override fun onServiceDisconnected(n: ComponentName) { engine = null }
        }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                // Invoke the exact UI callback without requiring a Google account or injecting OS taps.
                MainActivity::class.java.getDeclaredMethod("playBrowser", List::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
                    .invoke(activity, listOf(Track("aqz-KE-bpKQ", "Navigation soundcheck", "Blender Foundation", Source.YOUTUBE)), 0)
            }
            context.bindService(Intent(context, BrowserPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
            assertTrue(bound.await(10, TimeUnit.SECONDS))
            fun sample(name: String) {
                val state = BrowserPlaybackService.latest
                results.put(name, JSONObject().put("playing", state.playing).put("positionMs", state.position).put("diagnostics", state.status))
                results.getJSONObject(name).put("foreground", shell("dumpsys activity activities").lineSequence().firstOrNull { it.contains("topResumedActivity=") }.orEmpty().trim())
                java.io.File(context.filesDir, "browser-native-navigation-proof.json").writeText(results.toString(2))
            }
            fun click(label: String) = ui.onNode(hasText(label) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick) { it() }
            try {
                ui.waitUntil(30000) { BrowserPlaybackService.latest.duration > 0 }
                ins.runOnMainSync { engine!!.command("play") }
                ui.waitUntil(15000) { BrowserPlaybackService.latest.playing }
                click("Navigation soundcheck")
                ui.onNodeWithContentDescription("Back to library").assertExists()
                Thread.sleep(4000); sample("nativePlayer")
                ins.uiAutomation.takeScreenshot()?.let { bitmap ->
                    java.io.File(context.filesDir, "browser-real-artwork-player.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
                shell("dumpsys gfxinfo com.allen.vybe reset")
                recording = true
                ins.runOnMainSync { android.view.Choreographer.getInstance().postFrameCallback(frameProbe) }
                sampler.scheduleAtFixedRate({
                    val key = android.os.Looper.getMainLooper().thread.stackTrace.take(8).joinToString("\n")
                    stacks.merge(key, 1) { a, b -> a + b }
                }, 0, 100, TimeUnit.MILLISECONDS)
                val before = BrowserPlaybackService.latest.position
                ui.onNodeWithContentDescription("Back to library").performSemanticsAction(SemanticsActions.OnClick) { it() }
                Thread.sleep(5000); sample("inAppHome")
                assertTrue("Real YouTube paused when leaving native player", BrowserPlaybackService.latest.playing && BrowserPlaybackService.latest.position > before + 2500)
                click("Search"); Thread.sleep(5000); sample("search")
                assertTrue("YouTube paused on Search", BrowserPlaybackService.latest.playing)
                click("Your library"); Thread.sleep(5000); sample("library")
                assertTrue("YouTube paused on Library", BrowserPlaybackService.latest.playing)
                click("Home"); Thread.sleep(3000); sample("homeAgain")
                assertTrue("YouTube did not advance through navigation", BrowserPlaybackService.latest.position > before + 12000)
                java.io.File(context.filesDir, "browser-navigation-gfxinfo.txt").writeText(shell("dumpsys gfxinfo com.allen.vybe"))
            } finally {
                recording = false; sampler.shutdownNow()
                val sorted = frameIntervals.sorted()
                fun percentile(p: Double) = sorted.getOrNull(((sorted.size - 1) * p).toInt().coerceAtLeast(0)) ?: 0
                results.put("frameCallbacks", JSONObject().put("count", sorted.size).put("medianIntervalMs", percentile(.5)).put("p95IntervalMs", percentile(.95)).put("over50ms", sorted.count { it > 50 }))
                java.io.File(context.filesDir, "browser-main-thread-samples.txt").writeText(stacks.entries.sortedByDescending { it.value }.take(12).joinToString("\n\n") { "${it.value} samples\n${it.key}" })
                sample("final"); ins.runOnMainSync { engine?.command("stop") }; context.unbindService(connection)
            }
        }
    }
}

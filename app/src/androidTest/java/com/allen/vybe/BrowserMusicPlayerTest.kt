package com.allen.vybe

import android.content.*
import android.os.IBinder
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BrowserMusicPlayerTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun nativePlayerControlsRealBrowserAndShowsTimedLyrics() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val ctx = ins.targetContext
        val lyrics = java.io.File(ctx.filesDir, "lyrics-fixture.lrc")
        lyrics.writeText("[00:00.00]Timed lyrics fixture\n[00:04.00]Second timed test line")
        fun launcher() { ins.uiAutomation.executeShellCommand("am start -W -f 0x10008000 -n com.allen.vybe/.MainActivity").use { p -> java.io.FileInputStream(p.fileDescriptor).use { it.readBytes() } } }
        launcher()
        var engine: BrowserPlaybackService? = null
        val bound = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(n: ComponentName, b: IBinder) { engine = (b as BrowserPlaybackService.LocalBinder).service; bound.countDown() }
            override fun onServiceDisconnected(n: ComponentName) { engine = null }
        }
        try {
            ActivityScenario.launch<BrowserMusicActivity>(Intent(ctx, BrowserMusicActivity::class.java)
                .putStringArrayListExtra("queue", arrayListOf("fixture", "fixture"))
                .putStringArrayListExtra("titles", arrayListOf("VYBE browser soundcheck", "Second soundcheck"))
                .putStringArrayListExtra("artists", arrayListOf("Original test audio", "Original test audio"))).use { scenario ->
                scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                ctx.bindService(Intent(ctx, BrowserPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
                assertTrue(bound.await(10, TimeUnit.SECONDS))
                try {
                    ui.waitUntil(25000) { engine?.snapshot?.status?.contains("ready 4") == true }
                    ui.waitUntil(10000) { engine?.snapshot?.duration == 32000L }
                    ui.onNodeWithText("VYBE browser soundcheck").assertExists()
                    ui.waitUntil(10000) { ui.onAllNodesWithText("0:32").fetchSemanticsNodes().isNotEmpty() }
                    ins.uiAutomation.takeScreenshot()?.let { bitmap ->
                        java.io.File(ctx.filesDir, "browser-native-player-proof.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                    ui.onNodeWithContentDescription("Play browser playback").performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
                    ui.waitUntil(15000) { engine?.snapshot?.playing == true }
                    Thread.sleep(2000)
                    assertTrue("Browser time did not advance", engine!!.snapshot.position > 500)
                    ui.onNodeWithContentDescription("Pause browser playback").performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
                    ui.waitUntil(10000) { engine?.snapshot?.playing == false }
                    ui.onNodeWithText("Lyrics").performScrollTo().assertExists()
                    ui.onNodeWithText("Timed lyrics fixture").assertExists()
                    assertTrue("Generic list filtering did not run", engine!!.filterBlocks > 0)
                    launcher()
                    try {
                        ui.waitUntil(10000) { ui.onAllNodesWithContentDescription("Play YouTube").fetchSemanticsNodes().isNotEmpty() }
                    } catch(failure: Throwable) {
                        java.io.File(ctx.filesDir, "browser-mini-failure.txt").writeText("latest=" + BrowserPlaybackService.latest.toString() + "\n" + runCatching { ui.onRoot(useUnmergedTree = true).printToString() }.getOrDefault("Unable to inspect Compose root"))
                        throw failure
                    }
                    ui.onNodeWithContentDescription("Play YouTube").assertExists().performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
                    ui.waitUntil(10000) { engine?.snapshot?.playing == true }
                    ui.waitUntil(10000) { ui.onAllNodesWithContentDescription("Pause YouTube").fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithContentDescription("Pause YouTube").performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
                    ui.waitUntil(10000) { engine?.snapshot?.playing == false }
                } finally { launcher(); ins.runOnMainSync { engine?.command("stop") }; ctx.unbindService(connection) }
            }
        } finally { lyrics.delete() }
    }
}

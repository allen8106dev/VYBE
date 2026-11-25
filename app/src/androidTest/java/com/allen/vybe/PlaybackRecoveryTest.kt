package com.allen.vybe

import android.content.Intent
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.BrowserPlaybackService
import org.junit.Assert.*
import org.junit.Test

/** Local audio exercises the real service and Chromium without network/account dependencies. */
class PlaybackRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MuseApplication

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 30000
        while (!condition()) {
            assertTrue("$message: ${BrowserPlaybackService.latest}", android.os.SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(100)
        }
    }

    private fun browser(engine: BrowserPlaybackService) =
        BrowserPlaybackService::class.java.getDeclaredField("web").apply { isAccessible = true }.get(engine) as WebView

    @Test fun backgroundRendererLossRetainsQueueAndRestoresControlsAfterReopen() {
        foregroundTestApp()
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            var engine: BrowserPlaybackService? = null
            instrumentation.runOnMainSync {
                app.music.withEngine {
                    engine = it
                    it.load(listOf("fixture"), 0, listOf("Recovery first"))
                }
            }
            await("Service did not connect") { engine != null }
            val service = engine!!
            try {
                await("Fixture did not load") { BrowserPlaybackService.latest.duration == 32000L }
                instrumentation.runOnMainSync { service.seek(8000); service.command("play") }
                await("Fixture did not play") { BrowserPlaybackService.latest.playing && BrowserPlaybackService.latest.position >= 8000 }
                scenario.moveToState(Lifecycle.State.CREATED)
                lateinit var failed: WebView
                instrumentation.runOnMainSync { failed = browser(service); failed.loadUrl("chrome://crash") }
                await("Renderer was not replaced") {
                    var replaced = false
                    instrumentation.runOnMainSync { replaced = browser(service) !== failed }
                    replaced
                }
                scenario.moveToState(Lifecycle.State.RESUMED)
                await("Playback did not recover its position") {
                    BrowserPlaybackService.latest.let { it.playing && it.position >= 8000 && it.count == 1 && it.title == "Recovery first" }
                }
                assertSame("Controller lost the retained service", service, app.music.engine.value)
                instrumentation.runOnMainSync { service.command("pause") }
                await("Recovered pause did not work") { !BrowserPlaybackService.latest.playing }
                instrumentation.runOnMainSync { service.seek(16000) }
                await("Recovered seek/pause did not work") {
                    BrowserPlaybackService.latest.let { !it.playing && it.position in 15500..16500 }
                }
                // A second crash within the cooldown must stop automatic retries, while
                // an explicit new playback request must still work on the replacement browser.
                instrumentation.runOnMainSync { browser(service).loadUrl("chrome://crash") }
                await("Repeated failure did not offer retry") { BrowserPlaybackService.latest.error != null }
                instrumentation.runOnMainSync { service.load(listOf("fixture"), 0, listOf("Recovery second")) }
                await("Another song could not be selected after failure") {
                    BrowserPlaybackService.latest.let { it.title == "Recovery second" && it.duration == 32000L && it.error == null }
                }
                instrumentation.runOnMainSync { service.command("play") }
                await("Selected song did not play") { BrowserPlaybackService.latest.playing }
                scenario.recreate()
                assertSame("Activity recreation replaced playback", service, app.music.engine.value)
                instrumentation.runOnMainSync { service.seek(4000) }
                await("Controls failed after activity recreation") { BrowserPlaybackService.latest.position in 3500..7000 }
            } finally {
                instrumentation.runOnMainSync { service.command("stop") }
            }
        }
    }
}

package com.allen.vybe

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.BrowserPlaybackService
import org.junit.Assert.*
import org.junit.Test

/** Rotation must resize the existing playback host without stopping its audio. */
class OrientationPlaybackContinuityTest {
    @Test fun rotatingKeepsPlaybackAdvancingInTheSameActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MuseApplication
        foregroundTestApp()

        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 30000
            while (!condition()) {
                assertTrue("$message: ${BrowserPlaybackService.latest}", SystemClock.elapsedRealtime() < deadline)
                Thread.sleep(100)
            }
        }

        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { scenario ->
            scenario.onActivity {
                it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            var engine: BrowserPlaybackService? = null
            instrumentation.runOnMainSync {
                app.music.withEngine { service ->
                    engine = service
                    service.load(listOf("fixture"), 0, listOf("Rotation soundcheck"))
                }
            }
            await("Playback service did not connect") { engine != null }
            val service = engine!!
            try {
                await("Local playback fixture did not load") { BrowserPlaybackService.latest.duration == 32000L }
                instrumentation.runOnMainSync { service.command("play") }
                await("Local playback fixture did not start") { BrowserPlaybackService.latest.playing }

                var originalActivity: MainActivity? = null
                scenario.onActivity {
                    originalActivity = it
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                await("Activity did not receive the landscape configuration") {
                    originalActivity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
                scenario.onActivity { assertSame("Rotation recreated the playback host activity", originalActivity, it) }

                val before = BrowserPlaybackService.latest.position
                Thread.sleep(2500)
                val after = BrowserPlaybackService.latest
                assertTrue("Playback paused during rotation: $after", after.playing)
                assertTrue("Playback position stalled during rotation: $before -> ${after.position}", after.position >= before + 1800)
            } finally {
                instrumentation.runOnMainSync { service.command("stop") }
            }
        }
    }
}

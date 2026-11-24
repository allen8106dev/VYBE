package com.allen.vybe

import android.content.*
import android.media.session.*
import android.os.*
import android.app.KeyguardManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real YouTube webpage probe. Each outcome is recorded independently, including failures. */
@RunWith(AndroidJUnit4::class)
class YouTubeBackgroundProbeTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String): String = ins.uiAutomation.executeShellCommand(command).use { descriptor ->
        java.io.FileInputStream(descriptor.fileDescriptor).use { String(it.readBytes()) }
    }
    @Test fun recordRealYoutubeBackgroundAndSystemCommands() {
        val ctx = ins.targetContext
        val result = JSONObject()
        val output = java.io.File(ctx.filesDir, "youtube-background-probe.json")
        var service: BrowserPlaybackService? = null
        val bound = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(n: ComponentName, b: IBinder) { service = (b as BrowserPlaybackService.LocalBinder).service; bound.countDown() }
            override fun onServiceDisconnected(n: ComponentName) { service = null }
        }
        fun save() { output.writeText(result.toString(2)) }
        shell("am start -W -n com.allen.vybe/.MainActivity")
        val intent = Intent(ctx, BrowserMusicActivity::class.java).putStringArrayListExtra("queue", arrayListOf("aqz-KE-bpKQ", "M7lc1UVf-VE"))
        ActivityScenario.launch<BrowserMusicActivity>(intent).use {
            ctx.bindService(Intent(ctx, BrowserPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
            assertTrue(bound.await(10, TimeUnit.SECONDS))
            lateinit var controller: MediaController
            ins.runOnMainSync { controller = MediaController(ctx, service!!.mediaToken) }
            fun sample(label: String): JSONObject {
                val data = JSONObject()
                ins.runOnMainSync {
                    val state = controller.playbackState
                    data.put("state", state?.state).put("positionMs", state?.position).put("stateUpdatedMs", state?.lastPositionUpdateTime)
                        .put("sampleAtMs", SystemClock.elapsedRealtime()).put("diagnostics", service?.diagnostics)
                        .put("interactive", ctx.getSystemService(PowerManager::class.java).isInteractive)
                        .put("keyguardLocked", ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
                        .put("queue", controller.metadata?.getString("android.media.metadata.TITLE"))
                }
                data.put("foregroundActivity", shell("dumpsys activity activities").lineSequence().firstOrNull { it.contains("topResumedActivity=") }.orEmpty().trim())
                result.put(label, data); save(); return data
            }
            fun progressing(a: JSONObject, b: JSONObject) = b.optInt("state") == PlaybackState.STATE_PLAYING &&
                b.optLong("positionMs") > a.optLong("positionMs") + 3000 && b.optLong("sampleAtMs") - b.optLong("stateUpdatedMs") < 3000
            try {
                Thread.sleep(12000)
                ins.runOnMainSync { controller.transportControls.play() }
                var ready = false
                repeat(20) {
                    if (!ready) { ins.runOnMainSync { ready = controller.playbackState?.state == PlaybackState.STATE_PLAYING }; if(!ready) Thread.sleep(1000) }
                }
                val foreground = sample("foreground")
                result.put("foregroundPlaying", ready); save()
                if (!ready) return@use
                result.put("homeLaunch", shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"))
                Thread.sleep(8000)
                val home = sample("afterHome")
                result.put("homeProgress", progressing(foreground, home)); save()
                result.put("clockLaunch", shell("am start -W -a android.intent.action.SHOW_TIMERS"))
                Thread.sleep(8000)
                val other = sample("afterClock")
                result.put("clockProgress", progressing(home, other)); save()
                // Shell media-key routing exercises the system-selected active session.
                result.put("pauseDispatch", shell("cmd media_session dispatch pause"))
                Thread.sleep(2000)
                result.put("systemPause", sample("afterSystemPause").optInt("state") == PlaybackState.STATE_PAUSED); save()
                shell("cmd media_session dispatch play"); Thread.sleep(4000)
                val resumed = sample("afterSystemPlay")
                result.put("systemPlay", resumed.optInt("state") == PlaybackState.STATE_PLAYING); save()
                result.put("sleepCommand", shell("input keyevent 223"))
                Thread.sleep(2000)
                val off = sample("afterSleep")
                if (!off.optBoolean("interactive", true)) {
                    Thread.sleep(30000); val off30 = sample("screenOff30Seconds")
                    Thread.sleep(30000); val off60 = sample("screenOff60Seconds")
                    result.put("screenOff30Progress", progressing(off, off30)).put("screenOff60Progress", progressing(off30, off60))
                    shell("input keyevent 224")
                } else result.put("screenOffTest", "Shell sleep did not turn the display off; manual test required")
                save()
                shell("cmd media_session dispatch next"); Thread.sleep(8000)
                result.put("systemNext", sample("afterNext").optString("queue").contains("2/2"))
                shell("cmd media_session dispatch previous"); Thread.sleep(8000)
                result.put("systemPrevious", sample("afterPrevious").optString("queue").contains("1/2")); save()
            } finally {
                shell("am start -W -n com.allen.vybe/.MainActivity")
                ins.runOnMainSync { service?.command("stop") }; ctx.unbindService(connection); save()
            }
        }
    }
}

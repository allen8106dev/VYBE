package com.allen.vybe

import android.content.Intent
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.*
import org.junit.Assert.*
import org.junit.Test

/** Local audio reaches a real HTML ended event; suggestions are supplied without API calls. */
class AutoplayBoundaryTest {
    @Test fun disabledAutoplayStopsAndEnablingCanContinueWithoutReplacingUserQueue() {
        val ins=InstrumentationRegistry.getInstrumentation()
        val app=ins.targetContext.applicationContext as MuseApplication
        foregroundTestApp()
        fun await(message: String,condition: ()->Boolean) {
            val until=SystemClock.elapsedRealtime()+30000
            while(!condition()){assertTrue("$message: ${BrowserPlaybackService.latest}",SystemClock.elapsedRealtime()<until);Thread.sleep(100)}
        }
        ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java)).use { scenario ->
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            var engine: BrowserPlaybackService?=null
            ins.runOnMainSync { app.music.withEngine { service -> engine=service;service.autoplay(false);service.load(listOf("fixture"),0,listOf("Chosen soundcheck")) } }
            await("Service did not start"){engine!=null}
            val service=engine!!
            val next=QueueSong("aqz-KE-bpKQ","Suggested soundcheck","Blender Foundation")
            try {
                await("Fixture did not load"){BrowserPlaybackService.latest.duration==32000L}
                ins.runOnMainSync { service.command("play") }
                await("Fixture did not start"){BrowserPlaybackService.latest.let { it.playing && it.position>500 }}
                ins.runOnMainSync {
                    val web=BrowserPlaybackService::class.java.getDeclaredField("web").apply { isAccessible=true }.get(service) as WebView
                    service.offerSuggestions(service.queueVersion,"fixture",listOf(next))
                    web.evaluateJavascript("document.querySelector('audio').loop=false",null)
                    service.seek(31500)
                }
                await("Fixture did not reach its natural end"){BrowserPlaybackService.latest.let { !it.playing && it.position>=31900 }}
                assertEquals("fixture",BrowserPlaybackService.latest.videoId)
                ins.runOnMainSync { service.autoplay(true);service.offerSuggestions(service.queueVersion,"fixture",listOf(next)) }
                await("Suggestion did not continue after ended"){BrowserPlaybackService.latest.videoId==next.id}
                assertEquals(listOf("fixture",next.id),BrowserPlaybackService.latest.songs.map { song -> song.id })
                val staleVersion=service.queueVersion
                ins.runOnMainSync {
                    service.load(listOf("fixture"),0,listOf("A new chosen queue"))
                    service.offerSuggestions(staleVersion,"fixture",listOf(next))
                }
                assertEquals("fixture",BrowserPlaybackService.latest.videoId)
                assertFalse("Stale discovery changed the new queue",BrowserPlaybackService.latest.canNext)
                ins.runOnMainSync {
                    service.clearUpcoming()
                    service.offerSuggestions(service.queueVersion,"fixture",listOf(next))
                }
                assertFalse("Clearing up next must suppress autoplay",BrowserPlaybackService.latest.canNext)
            } finally { ins.runOnMainSync { service.autoplay(app.music.autoplay.value);service.command("stop") } }
        }
    }
}

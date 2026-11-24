package com.allen.vybe

import android.content.*
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*

/** Real system browser/session discovery and search; no fake search provider. */
class AssistantAndQueueTest {
    @Test fun assistantSearchPlaysAndQueueEditsKeepCurrentAudio() {
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val app=context.applicationContext as MuseApplication
        ins.uiAutomation.executeShellCommand("am start -W -f 0x10008000 -n com.allen.vybe/.MainActivity").use{p->java.io.FileInputStream(p.fileDescriptor).use{it.readBytes()}}
        var engine: BrowserPlaybackService?=null
        val connected=CountDownLatch(1);lateinit var browser: MediaBrowser;lateinit var controls: MediaController
        fun await(message: String,condition: ()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+60000
            while(!condition()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(250)
            assertTrue("$message: ${BrowserPlaybackService.latest.error?:BrowserPlaybackService.latest.status}",condition())
        }
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {
            it.onActivity{activity->activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
            ins.runOnMainSync {
                app.music.withEngine { service->engine=service }
                browser=MediaBrowser(context,ComponentName(context,BrowserPlaybackService::class.java),object: MediaBrowser.ConnectionCallback(){
                    override fun onConnected(){controls=MediaController(context,browser.sessionToken);connected.countDown()}
                },null);browser.connect()
            }
            assertTrue("System could not discover the VYBE media session",connected.await(15,TimeUnit.SECONDS))
            try {
                ins.runOnMainSync{controls.transportControls.playFromSearch("Imagine Dragons Believer",null)}
                await("Assistant song search did not start real playback"){BrowserPlaybackService.latest.playing&&BrowserPlaybackService.latest.videoId.matches(Regex("[A-Za-z0-9_-]{11}"))}
                val found=BrowserPlaybackService.latest
                assertTrue("No search queue was built",found.count>1)
                assertTrue("Metadata title is missing",controls.metadata?.getString("android.media.metadata.TITLE").orEmpty().isNotBlank())
                ins.runOnMainSync{controls.transportControls.pause()}
                await("System pause did not reach YouTube"){!BrowserPlaybackService.latest.playing}
                ins.runOnMainSync{controls.transportControls.play()}
                await("System play did not reach YouTube"){BrowserPlaybackService.latest.playing}
                ins.runOnMainSync{controls.transportControls.skipToNext()}
                await("System next did not change songs"){BrowserPlaybackService.latest.videoId!=found.videoId&&BrowserPlaybackService.latest.playing}
                ins.runOnMainSync{controls.transportControls.seekTo(0)}
                await("Seek did not reset song position"){BrowserPlaybackService.latest.position<2500}
                ins.runOnMainSync{controls.transportControls.skipToPrevious()}
                await("System previous did not return to the first song"){BrowserPlaybackService.latest.videoId==found.videoId&&BrowserPlaybackService.latest.playing}
                await("Real song duration is unavailable"){BrowserPlaybackService.latest.duration>10000}
                ins.runOnMainSync{controls.transportControls.seekTo(BrowserPlaybackService.latest.duration-1200)}
                await("Queue did not advance at the natural end"){BrowserPlaybackService.latest.videoId!=found.videoId&&BrowserPlaybackService.latest.playing}
                Thread.sleep(1500)
                val before=BrowserPlaybackService.latest
                ins.runOnMainSync{engine!!.move(before.count-1,1);engine!!.command("shuffle");engine!!.command("repeat");engine!!.sleep(15)}
                assertEquals(before.videoId,BrowserPlaybackService.latest.videoId)
                assertTrue(BrowserPlaybackService.latest.shuffle);assertEquals(1,BrowserPlaybackService.latest.repeat)
                assertTrue(BrowserPlaybackService.latest.sleepUntil>SystemClock.elapsedRealtime())
                assertTrue("Queue edits reset playback",BrowserPlaybackService.latest.position>=before.position)
                ins.runOnMainSync{engine!!.sleep(0);engine!!.clearUpcoming()}
                assertEquals(BrowserPlaybackService.latest.index+1,BrowserPlaybackService.latest.count)
                val evidence=org.json.JSONObject().put("systemBrowserConnected",true).put("searchQuery","Imagine Dragons Believer").put("videoId",found.videoId).put("title",found.title).put("searchQueueCount",found.count).put("systemPausePlay",true).put("systemNextPrevious",true).put("automaticQueueAdvance",true).put("queueEditsPreservedCurrent",true).put("timerSetAndCancelled",true)
                java.io.File(context.filesDir,"assistant-queue-proof.json").writeText(evidence.toString(2))
            } finally {ins.runOnMainSync{engine?.command("stop");browser.disconnect()}}
        }
    }
}

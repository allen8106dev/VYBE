package com.allen.vybe

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.BrowserPlaybackService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class VoiceSearchTest {
    @get:Rule val ui=createEmptyComposeRule()
    @get:Rule val timeout=org.junit.rules.Timeout.seconds(120)
    @Test fun voiceResultPlaysRealSongAndCancellationLeavesPlaybackAlone() {
        val ins=InstrumentationRegistry.getInstrumentation();val ctx=ins.targetContext
        val app=ctx.applicationContext as MuseApplication
        fun stage(name: String){java.io.File(ctx.filesDir,"voice-test-stage.txt").writeText(name)}
        val requests=AtomicInteger(0)
        var response=Instrumentation.ActivityResult(Activity.RESULT_OK,
            Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,arrayListOf("Imagine Dragons Believer")))
        val monitor=object: Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if(intent.action!=RecognizerIntent.ACTION_RECOGNIZE_SPEECH)return null
                assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
                requests.incrementAndGet();return response
            }
        }
        ins.addMonitor(monitor)
        fun shell(command: String)=ins.uiAutomation.executeShellCommand(command).use{p->java.io.FileInputStream(p.fileDescriptor).use{it.readBytes()}}
        shell("am start -W -f 0x10008000 -n com.allen.vybe/.MainActivity")
        try {
            ActivityScenario.launch<MainActivity>(Intent(ctx,MainActivity::class.java)).use { scenario ->
                scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
                ui.onNode(hasText("Search") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
                ui.onNodeWithContentDescription("Find and play a song by voice").performSemanticsAction(SemanticsActions.OnClick){it()}
                ui.waitUntil(60000){BrowserPlaybackService.latest.playing&&app.music.requestedSearch.value?.query=="Imagine Dragons Believer"}
                stage("real playback started")
                assertEquals(1,requests.get());assertTrue(app.music.requestedSearch.value!!.tracks.isNotEmpty())
                val before=BrowserPlaybackService.latest
                response=Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
                ui.onNodeWithContentDescription("Find and play a song by voice").performSemanticsAction(SemanticsActions.OnClick){it()}
                ui.waitUntil(5000){requests.get()==2};ui.waitForIdle()
                stage("cancel completed")
                assertEquals(before.videoId,BrowserPlaybackService.latest.videoId)
                assertTrue(BrowserPlaybackService.latest.playing)
                assertEquals("Imagine Dragons Believer",app.music.requestedSearch.value?.query)
                ui.onNode(hasText("Home") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
                ui.onNodeWithText("Your next\nfavourite.").assertIsDisplayed()
                ui.onNodeWithText("Find music").performSemanticsAction(SemanticsActions.OnClick){it()}
                ui.waitForIdle();assertEquals("Opening Search relaunched the microphone",2,requests.get())
                stage("return to search complete")
                val proof=org.json.JSONObject().put("recognizerResultInjected",true).put("liveYoutubePlayback",true)
                    .put("query","Imagine Dragons Believer").put("videoId",before.videoId).put("title",before.title)
                    .put("cancelPreservedPlayback",true)
                java.io.File(ctx.filesDir,"voice-search-proof.json").writeText(proof.toString(2))
                ins.removeMonitor(monitor)
            }
        } finally {ins.removeMonitor(monitor);ins.runOnMainSync{app.music.command("stop")}}
    }
}

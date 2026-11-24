package com.allen.vybe

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.BrowserPlaybackService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VoiceControlsTest {
    @get:Rule val ui=createEmptyComposeRule()
    @get:Rule val timeout=org.junit.rules.Timeout.seconds(120)
    @Test fun spokenCommandDispatcherControlsRealPlaybackAndHomeNavigation() {
        val ins=InstrumentationRegistry.getInstrumentation();val ctx=ins.targetContext
        val app=ctx.applicationContext as MuseApplication
        ins.uiAutomation.executeShellCommand("am start -W -f 0x10008000 -n com.allen.vybe/.MainActivity").use{p->java.io.FileInputStream(p.fileDescriptor).use{it.readBytes()}}
        ActivityScenario.launch<MainActivity>(Intent(ctx,MainActivity::class.java)).use{scenario ->
            scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
            fun say(text: String){ins.runOnMainSync{app.music.handleVoice(text)}}
            try {
                say("play Imagine Dragons Believer")
                ui.waitUntil(60000){BrowserPlaybackService.latest.playing&&app.music.requestedSearch.value?.tracks?.isNotEmpty()==true}
                val song=BrowserPlaybackService.latest.videoId
                say("pause the music");ui.waitUntil(10000){!BrowserPlaybackService.latest.playing}
                say("resume");ui.waitUntil(10000){BrowserPlaybackService.latest.playing}
                say("shuffle on");ui.waitUntil(5000){BrowserPlaybackService.latest.shuffle}
                say("repeat this song");ui.waitUntil(5000){BrowserPlaybackService.latest.repeat==2}
                say("sleep timer for 30 minutes");ui.waitUntil(5000){BrowserPlaybackService.latest.sleepUntil>android.os.SystemClock.elapsedRealtime()}
                say("cancel timer");ui.waitUntil(5000){BrowserPlaybackService.latest.sleepUntil==0L}
                say("go home");ui.onNodeWithText("Your next\nfavourite.").assertIsDisplayed()
                ui.onNodeWithText("Search songs or artists").assertDoesNotExist()
                ui.onNode(hasText("Search") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
                ui.onNodeWithText("Your next\nfavourite.").assertDoesNotExist()
                ui.onNode(hasText("Home") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}
                ui.onNodeWithText("Your next\nfavourite.").assertIsDisplayed()
                scenario.recreate()
                ui.onNodeWithText("Your next\nfavourite.").assertIsDisplayed()
                say("search for Coldplay")
                ui.waitUntil(30000){app.music.requestedSearch.value?.let{it.query=="Coldplay"&&!it.loading&&it.tracks.isNotEmpty()}==true}
                assertEquals("Searching replaced the playing song",song,BrowserPlaybackService.latest.videoId)
                assertTrue(BrowserPlaybackService.latest.playing)
                java.io.File(ctx.filesDir,"voice-controls-proof.json").writeText("{\"liveYoutubePlayback\":true,\"pauseResumeShuffleRepeatTimer\":true,\"homeButtonAndVoiceNavigation\":true,\"rotationPreservedHome\":true,\"searchPreservedCurrentSong\":true}")
            } finally {ins.runOnMainSync{app.music.engine.value?.sleep(0);app.music.command("stop")}}
        }
    }
}

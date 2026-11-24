package com.allen.vybe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*

class MuseNavigationTest {
    @get:Rule val ui=createEmptyComposeRule()
    private fun screenshot(name: String) {
        val ins=InstrumentationRegistry.getInstrumentation()
        ins.uiAutomation.takeScreenshot()?.let {bitmap->java.io.File(ins.targetContext.filesDir,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
    @Test fun youtubeOnlyNavigationAndLocalPlaylistCreation() {
        val ins=InstrumentationRegistry.getInstrumentation()
        val app=ins.targetContext.applicationContext as MuseApplication
        ins.uiAutomation.executeShellCommand("am start -W -f 0x10008000 -n com.allen.vybe/.MainActivity").use{p->java.io.FileInputStream(p.fileDescriptor).use{it.readBytes()}}
        val name="UI check ${System.currentTimeMillis()}"
        ActivityScenario.launch<MainActivity>(Intent(ins.targetContext,MainActivity::class.java)).use {scenario->
        scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        try {
            ui.onNodeWithText("Your next\nfavourite.").assertIsDisplayed();screenshot("ui-home")
            ui.onNode(hasText("Search") and hasClickAction()).performClick()
            ui.onNodeWithText("Search songs or artists").assertExists()
            ui.onNodeWithText("Originals").assertDoesNotExist();ui.onNodeWithText("Original sessions").assertDoesNotExist()
            scenario.recreate()
            ui.onNodeWithText("Search songs or artists").assertExists()
            ui.onNode(hasText("Your library") and hasClickAction()).performClick()
            ui.onNodeWithContentDescription("Create playlist").performClick()
            ui.onNodeWithText("Playlist name").performTextInput(name)
            ui.onNodeWithText("Create").performClick()
            ui.waitUntil(10000){ui.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()}
            screenshot("ui-library")
            ui.onNodeWithText(name).performClick();ui.onNodeWithText("Your mix starts here").assertExists()
            ui.onNodeWithContentDescription("Back").performClick()
            ui.onNode(hasText("Home") and hasClickAction()).performClick()
            ui.onNodeWithContentDescription("Settings").performClick()
            ui.onNodeWithText("Make yourself at home.").assertExists();screenshot("ui-settings")
            ui.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Hands-free listening"))
            ui.onNodeWithText("Hands-free listening").assertIsDisplayed()
        } finally {
            runBlocking {app.database.library().playlists().filter{it.title==name}.forEach{app.localPlaylists.delete(it.id)}}
        }
        }
    }
}

package com.allen.vybe

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.domain.Track
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SongTapNavigationTest {
    @get:Rule val ui=createEmptyComposeRule()

    @Test fun searchSongTapStaysOnSearchAndOnlyMiniPlayerOpensPlayer() {
        val ins=InstrumentationRegistry.getInstrumentation()
        val app=ins.targetContext.applicationContext as MuseApplication
        foregroundTestApp()
        ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java)).use { scenario ->
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            ui.onNode(hasText("Search") and hasClickAction()).performClick()
            scenario.onActivity { activity ->
                val vm=ViewModelProvider(activity)[MuseViewModel::class.java]
                @Suppress("UNCHECKED_CAST")
                val search=MuseViewModel::class.java.getDeclaredField("mutableSearch").apply { isAccessible=true }.get(vm) as MutableStateFlow<SearchState>
                search.value=SearchState(query="Navigation test",submitted=true,tracks=listOf(Track("aqz-KE-bpKQ","Song tap test","Blender Foundation")))
            }
            try {
                ui.onNodeWithText("Song tap test").performScrollTo().performClick()
                ui.onNodeWithContentDescription("Submit search").assertExists()
                ui.onNodeWithContentDescription("Back to library").assertDoesNotExist()
                ui.waitUntil(10000) { ui.onAllNodesWithContentDescription("Mini-player song artwork").fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasContentDescription("Mini-player song artwork") and hasClickAction()).performClick()
                ui.onNodeWithContentDescription("Back to library").assertExists()
                ui.onNodeWithContentDescription("Back to library").performClick()
                ui.onNodeWithContentDescription("Submit search").assertExists()
            } finally { ins.runOnMainSync { app.music.command("stop") } }
        }
    }
}

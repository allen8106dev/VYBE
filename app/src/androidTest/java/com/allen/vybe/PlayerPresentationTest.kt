package com.allen.vybe

import android.graphics.Bitmap
import android.graphics.Color
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.lyrics.LyricLine
import com.allen.vybe.ui.theme.VYBETheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerPresentationTest {
    @get:Rule val ui=createEmptyComposeRule()

    @Test fun fullLyricsCanScrollToTheEndAndClose() {
        foregroundTestApp()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
        scenario.onActivity { activity -> activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);activity.setContent {
            var open by remember { mutableStateOf(true) }
            VYBETheme {
                if(open)FullScreenLyrics("Local lyrics test","VYBE",(0..39).map { LyricLine(it*1000L,"Original test line $it") },"","Saved lyrics",3000){open=false}
            }
        } }
        ui.onNodeWithText("Original test line 0").assertIsDisplayed()
        ui.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Original test line 39"))
        ui.onNodeWithText("Original test line 39").assertIsDisplayed()
        ui.onNodeWithContentDescription("Close full-screen lyrics").performClick()
        ui.onNodeWithText("Local lyrics test").assertDoesNotExist()
        }
    }

    @Test fun thumbnailBarsAreRemovedWhileSquareAndDarkArtworkStayIntact() {
        val letterbox=Bitmap.createBitmap(480,360,Bitmap.Config.ARGB_8888)
        letterbox.eraseColor(Color.BLACK)
        val canvas=android.graphics.Canvas(letterbox)
        canvas.drawRect(0f,45f,480f,315f,android.graphics.Paint().apply { color=Color.GREEN })
        val cropped=trimThumbnailBars(letterbox)
        assertEquals(270,cropped.height);assertEquals(Color.GREEN,cropped.getPixel(240,0))
        val dark=Bitmap.createBitmap(480,360,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val square=Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        assertSame(dark,trimThumbnailBars(dark));assertSame(square,trimThumbnailBars(square))
        cropped.recycle();letterbox.recycle();dark.recycle();square.recycle()
    }
}

/** MIUI requires shell foregrounding before instrumentation can launch an activity. */
internal fun foregroundTestApp() {
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am start -W -f 0x10008000 -n com.allen.vybe/.MainActivity").use { descriptor ->
        java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
    }
}

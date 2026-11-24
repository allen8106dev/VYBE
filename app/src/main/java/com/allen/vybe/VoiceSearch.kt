package com.allen.vybe

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** Tap-to-talk fallback using the phone's speech UI when the local listener is disabled. */
@Composable
fun rememberSongVoiceSearch(onSong: (String)->Unit,onError: (String)->Unit): ()->Unit {
    val currentSong=rememberUpdatedState(onSong)
    val currentError=rememberUpdatedState(onError)
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK){
            val query=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }?.trim()?.take(200)
            if(query.isNullOrBlank())currentError.value("Couldn't hear a song name. Try again or type your search.")
            else currentSong.value(query)
        }
    }
    return remember(launcher) {{
        try {
            launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT,"Say a song, or a command like pause, next, shuffle or go home")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3))
        }catch(_: ActivityNotFoundException){
            currentError.value("Voice search isn't available on this phone. Type a song or artist instead.")
        }catch(_: SecurityException){
            currentError.value("The phone couldn't open voice search. Type a song or artist instead.")
        }
    }}
}

package com.allen.vybe

import android.os.Bundle
import android.content.*
import android.os.IBinder
import androidx.compose.runtime.*
import com.allen.vybe.browser.BrowserPlaybackService
import com.allen.vybe.domain.Track
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.allen.vybe.ui.theme.VYBETheme
import com.google.android.gms.auth.api.identity.*

class MainActivity : ComponentActivity() {
    private var incomingSearch by mutableStateOf(0)
    private var incomingVoice by mutableStateOf(0)
    private fun playBrowser(tracks: List<Track>, index: Int) {
        model.play(tracks,index)
    }
    private val model by lazy { ViewModelProvider(this)[MuseViewModel::class.java] }
    private val authorization = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.data == null) model.authFailed()
        else runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data) }
            .onSuccess { model.connect(it.accessToken) }.onFailure { model.authFailed(it) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingSearch=savedInstanceState?.getInt("incomingSearch")?:0
        incomingVoice=savedInstanceState?.getInt("incomingVoice")?:0
        if(savedInstanceState==null)handleIntent(intent)
        setContent { VYBETheme { MuseApp(model, onConnect = model::authorize, onDisconnect = ::disconnect, incomingSearch = incomingSearch, incomingVoice = incomingVoice) } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.authorization.collect { pending ->
                    if (pending != null) {
                        model.authorizationLaunched()
                        runCatching { authorization.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }.onFailure { model.authFailed(it) }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);handleIntent(intent) }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("incomingSearch",incomingSearch);outState.putInt("incomingVoice",incomingVoice)
        super.onSaveInstanceState(outState)
    }
    private fun handleIntent(intent: Intent) {
        if(intent.action==ACTION_VOICE_SEARCH){incomingSearch++;incomingVoice++;return}
        if(intent.action==android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH||intent.action==Intent.ACTION_SEARCH){
            val query=intent.getStringExtra(android.app.SearchManager.QUERY)?.trim()?.take(200).orEmpty().ifBlank {
                listOf(intent.getStringExtra(android.provider.MediaStore.EXTRA_MEDIA_TITLE),intent.getStringExtra(android.provider.MediaStore.EXTRA_MEDIA_ARTIST)).filterNotNull().joinToString(" ").take(200)
            }
            if(query.isNotBlank()){
                incomingSearch++
                if(intent.action==android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)model.music.playSearch(query) else model.search(query)
            } else if(intent.action==android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)model.music.command("play") else incomingSearch++
        }
    }
    companion object {const val ACTION_VOICE_SEARCH="com.allen.vybe.action.VOICE_SEARCH"}
    private fun disconnect() {
        model.disconnect()?.let { token ->
            Identity.getAuthorizationClient(this).clearToken(ClearTokenRequest.builder().setToken(token).build())
                .addOnFailureListener(this) { model.notify("Disconnected locally. Manage Google access in Settings to revoke your grant.") }
        }
    }
}

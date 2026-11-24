package com.allen.vybe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.allen.vybe.designsystem.Muted

@Composable
fun WakeVoiceSettings(notify: (String)->Unit) {
    val context=LocalContext.current
    val state by WakeVoiceService.state.collectAsStateWithLifecycle()
    fun start() {
        runCatching {ContextCompat.startForegroundService(context,Intent(context,WakeVoiceService::class.java))}
            .onFailure {notify("Could not start listening. Keep VYBE open and try again.")}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result ->
        if(result[Manifest.permission.RECORD_AUDIO]==true||ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)start()
        else notify("Microphone permission is needed for Hey Music. You can still use the Search microphone.")
    }
    SettingsGroup {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Mic,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text("Hey Music",fontWeight=FontWeight.Bold);Text(state.status,style=MaterialTheme.typography.bodySmall,color=Muted)}
            Switch(state.enabled,onCheckedChange={enabled ->
                if(!enabled)context.stopService(Intent(context,WakeVoiceService::class.java))
                else permission.launch(if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS)else arrayOf(Manifest.permission.RECORD_AUDIO))
            })
        }
        Text("Say “Hey Music”, wait for the vibration, then “pause”, “next song”, “play Believer by Imagine Dragons”, “shuffle on”, or “go home”. Say “Hey Music, stop listening” to turn it off. English commands and song search work directly in VYBE.",style=MaterialTheme.typography.bodySmall)
        Text("Listens on this phone, including while locked. No audio is saved or uploaded. Continuous listening uses extra battery. Stop here or from the microphone notification. Enable again after restarting the app or phone.",style=MaterialTheme.typography.bodySmall,color=Muted)
        if(state.heard.isNotBlank())Text("Last command: ${state.heard}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
    }}
}

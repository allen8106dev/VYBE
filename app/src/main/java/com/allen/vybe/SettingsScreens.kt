package com.allen.vybe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.allen.vybe.browser.*
import com.allen.vybe.designsystem.*

@Composable
fun SettingsScreen(vm: MuseViewModel,back: ()->Unit,connect: ()->Unit,disconnect: ()->Unit,browser: ()->Unit,open: (String)->Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val player by BrowserPlaybackService.state.collectAsStateWithLifecycle()
    val engine by vm.music.engine.collectAsStateWithLifecycle()
    val autoplay by vm.music.autoplay.collectAsStateWithLifecycle()
    var clear by rememberSaveable{mutableStateOf(false)}
    var signOut by rememberSaveable{mutableStateOf(false)}
    var timer by rememberSaveable{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item{PageTitle("Settings","Make yourself at home.",back)}
        item {
            Card(shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.AccountCircle,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary)
                    Text(if(account.connected)"You're connected" else "Your music, connected",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text(if(account.connected)"Search YouTube and keep your playlists together." else "Connect YouTube to find songs and bring your playlists along.",color=Muted)
                    if(account.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                    account.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                    if(account.connected)OutlinedButton(onClick={signOut=true}){Text("Disconnect account")}
                    else Button(onClick=connect,enabled=!account.loading){Text("Connect YouTube")}
                }
            }
        }
        item{SectionTitle("Playback")}
        item{SettingsGroup {
            ListItem(headlineContent={Text("Autoplay")},supportingContent={Text("Continue with suggested songs when your queue ends")},leadingContent={Icon(Icons.Rounded.AutoAwesome,null)},trailingContent={Switch(autoplay,vm.music::setAutoplay)},colors=ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent))
            ActionRow(Icons.Rounded.Bedtime,"Sleep timer"){timer=true}
            Text(if(player.sleepUntil>0)"Music pauses when your timer ends." else "Let the music fade into your night.",Modifier.padding(start=56.dp,end=18.dp,bottom=16.dp),style=MaterialTheme.typography.bodySmall,color=Muted)
        }}
        item{SectionTitle("Your assistant")}
        item{WakeVoiceSettings(vm::notify)}
        item{SettingsGroup {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.AutoAwesome,null,tint=MaterialTheme.colorScheme.primary);Text("Hands-free listening",Modifier.padding(start=12.dp),fontWeight=FontWeight.Bold)}
            Text("Try asking Gemini:",color=Muted)
            Text("“Pause the music”\n“Resume”\n“Next song”",style=MaterialTheme.typography.bodyLarge)
            Text("For song requests, tap the microphone in Search and say a title and artist. You can also hold the VYBE app icon and choose Voice search.",style=MaterialTheme.typography.bodySmall,color=Muted)
            Text("If Gemini mishears VYBE, try spelling “V Y B E”. Song requests through Gemini depend on Google recognizing and routing to this app.",style=MaterialTheme.typography.bodySmall,color=Muted)
        }}}
        item{SectionTitle("Storage & privacy")}
        item{SettingsGroup {
            ActionRow(Icons.Rounded.History,"Clear listening and search history"){clear=true}
            HorizontalDivider(Modifier.padding(horizontal=20.dp))
            ActionRow(Icons.Rounded.ManageAccounts,"Manage Google access"){open("https://myaccount.google.com/permissions")}
            Text("Likes and local playlists stay on this phone. Audio is streamed; no songs are downloaded. Google access tokens stay in memory.",Modifier.padding(20.dp),style=MaterialTheme.typography.bodySmall,color=Muted)
        }}
        item{SectionTitle("About VYBE")}
        item{SettingsGroup {
            ActionRow(Icons.Rounded.Description,"YouTube terms"){open("https://www.youtube.com/t/terms")}
            ActionRow(Icons.Rounded.PrivacyTip,"Google privacy policy"){open("https://policies.google.com/privacy")}
            Text("VYBE 1.0\nA little space for sound.",Modifier.padding(20.dp),style=MaterialTheme.typography.bodySmall,color=Muted)
        }}
        item{Spacer(Modifier.height(12.dp))}
    }
    if(timer)SleepTimerDialog(player.sleepUntil,{timer=false}){minutes->engine?.sleep(minutes);if(engine==null)vm.notify("Start a song before setting a sleep timer.");timer=false}
    if(signOut)AlertDialog(onDismissRequest={signOut=false},title={Text("Disconnect YouTube?")},text={Text("Your local likes and playlists stay on this phone. You can reconnect any time.")},confirmButton={TextButton(onClick={disconnect();signOut=false}){Text("Disconnect")}},dismissButton={TextButton(onClick={signOut=false}){Text("Cancel")}})
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text("Clear your history?")},text={Text("This removes listening history and recent searches from this phone. Likes and playlists stay saved.")},confirmButton={TextButton(onClick={vm.clearHistory();clear=false}){Text("Clear history")}},dismissButton={TextButton(onClick={clear=false}){Text("Cancel")}})
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){Column(content=content)}
}

@Composable
fun SleepTimerDialog(until: Long,dismiss: ()->Unit,set: (Int)->Unit) {
    AlertDialog(onDismissRequest=dismiss,title={Text("Sleep timer")},text={Column {
        Text("Pause playback after",color=Muted,modifier=Modifier.padding(bottom=10.dp))
        listOf(15,30,45,60,90).forEach{minutes->TextButton(onClick={set(minutes)},modifier=Modifier.fillMaxWidth()){Text("$minutes minutes")}}
        if(until>0)TextButton(onClick={set(0)},modifier=Modifier.fillMaxWidth()){Text("Turn off timer")}
    }},confirmButton={TextButton(onClick=dismiss){Text("Cancel")}})
}

@Composable
fun BrowserScreen(back: () -> Unit) {
    val labContext = androidx.compose.ui.platform.LocalContext.current
    var diagnostics by remember { mutableStateOf(BrowserDiagnostics()) }
    var address by rememberSaveable { mutableStateOf("https://muse.invalid/lab") }
    var javascript by rememberSaveable { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var session by remember { mutableStateOf<BrowserSession?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session) {
        val current = session
        val observer = LifecycleEventObserver { _, event -> when (event) { Lifecycle.Event.ON_RESUME -> current?.resume(); Lifecycle.Event.ON_PAUSE -> current?.pause(); else -> Unit } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PageTitle("Browser lab", back = back)
        TextButton(onClick = { labContext.startActivity(android.content.Intent(labContext, com.allen.vybe.browser.BrowserPlaybackActivity::class.java).putStringArrayListExtra("queue", arrayListOf("fixture", "fixture"))) }) { Text("Open browser media + Rust filter proof") }
        TextButton(onClick = { labContext.startActivity(android.content.Intent(labContext, BrowserMusicActivity::class.java).putStringArrayListExtra("queue", arrayListOf("aqz-KE-bpKQ", "M7lc1UVf-VE")).putStringArrayListExtra("titles", arrayListOf("Big Buck Bunny", "YouTube player demo"))) }) { Text("Open YouTube browser playback test") }
        OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("HTTPS address") }, trailingIcon = { TextButton(onClick = { session?.navigate(address) }) { Text("Go") } })
        Row {
            TextButton(onClick = { session?.back() }) { Text("Back") }
            TextButton(onClick = { session?.reload() }) { Text("Reload") }
            TextButton(onClick = { session?.clearData() }) { Text("Clear data") }
        }
        Row { Text("JavaScript", modifier = Modifier.weight(1f)); Switch(javascript, { javascript = it; session?.javascript(it) }) }
        Text("${diagnostics.requests} requests ? ${diagnostics.blocked} blocked ? fixture rules only", style = MaterialTheme.typography.bodySmall)
        Text(diagnostics.status, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
        key(generation) {
            AndroidView(factory = { context -> BrowserSession(context, { diagnostics = it }, { generation++ }).also { session = it; if (javascript) it.javascript(true) } }, modifier = Modifier.fillMaxWidth().weight(1f), onRelease = { it.close(); if (session === it) session = null })
        }
    }
}

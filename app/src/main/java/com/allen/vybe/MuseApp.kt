package com.allen.vybe

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.allen.vybe.browser.BrowserPlaybackService
import com.allen.vybe.designsystem.*
import com.allen.vybe.domain.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuseApp(vm: MuseViewModel,onConnect: ()->Unit,onDisconnect: ()->Unit,incomingSearch: Int=0,incomingVoice: Int=0) {
    var screens by rememberSaveable {mutableStateOf(listOf("home"))}
    val route=screens.last()
    val screenState=rememberSaveableStateHolder()
    val engine by vm.music.engine.collectAsStateWithLifecycle()
    val presence=remember { BrowserPlaybackService.state.map { it.videoId.isNotEmpty() }.distinctUntilChanged() }
    val active by presence.collectAsStateWithLifecycle(initialValue=BrowserPlaybackService.latest.videoId.isNotEmpty())
    val message by vm.message.collectAsStateWithLifecycle()
    val musicMessage by vm.music.message.collectAsStateWithLifecycle()
    val snackbar=remember { SnackbarHostState() }
    val context=LocalContext.current
    var options by remember { mutableStateOf<Track?>(null) }
    var adding by remember { mutableStateOf<Track?>(null) }
    var handledVoice by rememberSaveable {mutableIntStateOf(0)}
    var handledVoiceNavigation by rememberSaveable {mutableLongStateOf(0L)}
    val voiceNavigation by vm.music.voiceNavigation.collectAsStateWithLifecycle()
    var handledSearch by rememberSaveable {mutableIntStateOf(0)}
    var consent by rememberSaveable { mutableStateOf(false) }
    fun go(path: String) {
        if(path==route)return
        screens=if(path in setOf("home","search","library"))listOf("home")+(if(path=="home")emptyList() else listOf(path)) else screens+path
    }
    fun back(){screens=if(screens.size>1)screens.dropLast(1) else listOf("home")}
    BackHandler(screens.size>1){back()}
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,url.toUri())) }.onFailure { vm.notify("No app can open this link.") } }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it);vm.clearMessage() } }
    LaunchedEffect(musicMessage) { musicMessage?.let { snackbar.showSnackbar(it);vm.music.clearMessage() } }
    LaunchedEffect(incomingSearch) { if(incomingSearch>handledSearch){handledSearch=incomingSearch;go("search")} }
    val phoneVoiceSearch=rememberSongVoiceSearch(onSong=vm.music::handleVoice,onError=vm::notify)
    val voiceSearch: ()->Unit={
        if(WakeVoiceService.state.value.enabled)context.startService(Intent(context,WakeVoiceService::class.java).setAction(WakeVoiceService.ARM))
        else phoneVoiceSearch()
    }
    LaunchedEffect(incomingVoice){if(incomingVoice>handledVoice){handledVoice=incomingVoice;voiceSearch()}}
    LaunchedEffect(voiceNavigation){if(voiceNavigation.first>handledVoiceNavigation){handledVoiceNavigation=voiceNavigation.first;go(voiceNavigation.second)}}
    val play: (Track)->Unit={track ->
        val candidates=when { route.startsWith("playlist/")->vm.selected.value.tracks;route=="search"->vm.search.value.tracks;else->listOf(track) }
        val tracks=if(candidates.any { it.id==track.id })candidates else listOf(track)
        vm.play(tracks,tracks.indexOfFirst { it.id==track.id }.coerceAtLeast(0))
    }
    Box(Modifier.fillMaxSize()) {
        BrowserEngineHost(engine)
        Scaffold(snackbarHost={SnackbarHost(snackbar)},bottomBar={
            if(route!="browser-player"&&route!="queue"&&route!="browser")Column {
                if(active)ConnectedBrowserMiniPlayer({go("browser-player")},vm.music::command,vm)
                NavigationBar(containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp) {
                    listOf(Triple("home","Home",Icons.Rounded.Home),Triple("search","Search",Icons.Rounded.Search),Triple("library","Your library",Icons.Rounded.LibraryMusic)).forEach { (path,label,icon)->
                        NavigationBarItem(selected=route==path,onClick={go(path)},icon={Icon(icon,label)},label={Text(label)})
                    }
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            screenState.SaveableStateProvider(route) {
                when {
                    route=="home" -> HomeScreen(vm,{go("settings")},{query->if(query.isNotBlank())vm.search(query);go("search")},play,{options=it},{tab->vm.libraryTab.value=tab;go("library")},voiceSearch)
                    route=="search" -> SearchScreen(vm,play,{options=it},{open("https://www.youtube.com/channel/$it")},{go("settings")},onVoice=voiceSearch)
                    route=="library" -> LibraryScreen(vm,{go("playlist/${it.id}/${it.cloud}")},play,{options=it},{go("settings")})
                    route.startsWith("playlist/") -> {
                        val parts=route.split('/')
                        LaunchedEffect(route) {vm.select(parts.getOrElse(1){""},parts.getOrElse(2){"false"}=="true")}
                        PlaylistScreen(vm,::back,{tracks,index,shuffle->vm.play(tracks,index,shuffle)},{options=it},{go("search")})
                    }
                    route=="browser-player" -> ConnectedBrowserPlayerScreen(engine,::back,{options=it},{go("queue")},vm,{adding=it})
                    route=="queue" -> MusicQueueScreen(engine,::back)
                    route=="settings" -> SettingsScreen(vm,::back,{consent=true},onDisconnect,{go("browser")},::open)
                    route=="browser" -> BrowserScreen(::back)
                }
            }
            }
        }
    }
    options?.let { track -> SongActions(vm,track,{options=null},{adding=track;options=null}) }
    adding?.let { AddToPlaylistDialog(vm,it){adding=null} }
    if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("Connect YouTube")},text={Text("Connect your Google account to search YouTube and manage your playlists. Your local likes and playlists stay on this phone. YouTube audio is streamed and is never downloaded. Google authorization and YouTube's terms apply.")},confirmButton={Button(onClick={consent=false;onConnect()}){Text("Continue with Google")}},dismissButton={TextButton(onClick={consent=false}){Text("Cancel")}})
}


@Composable
fun HomeScreen(vm: MuseViewModel,settings: ()->Unit,search: (String)->Unit,play: (Track)->Unit,more: (Track)->Unit,library: (String)->Unit,voice: ()->Unit={}) {
    val recent by vm.recentSongs.collectAsStateWithLifecycle()
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val recommendations by vm.music.recommendations.collectAsStateWithLifecycle()
    LaunchedEffect(account.connected, recent.firstOrNull()?.id, liked.firstOrNull()?.id) {
        if(account.connected)vm.music.refreshRecommendations(recent.firstOrNull() ?: liked.firstOrNull())
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("VYBE",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)
                ActionIcon(Icons.Rounded.Mic,"Voice assistant",click=voice)
                ActionIcon(Icons.Rounded.Settings,"Settings",click=settings)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF284D3A),Color(0xFF152B25)))).padding(26.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("PRESS PLAY ON SOMETHING GOOD",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                Text("Your next\nfavourite.",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold)
                Button(onClick={search("")},contentPadding=PaddingValues(horizontal=22.dp,vertical=12.dp)){Icon(Icons.Rounded.Search,null);Spacer(Modifier.width(8.dp));Text("Find music")}
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CollectionTile("Liked songs","${liked.size} saved",Icons.Rounded.Favorite,Modifier.weight(1f)){library("Liked songs")}
                CollectionTile("Your playlists","Made for your days",Icons.Rounded.LibraryMusic,Modifier.weight(1f)){library("Playlists")}
            }
        }
        if(!account.connected)item {
            Card(onClick=settings,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.AccountCircle,null,tint=MaterialTheme.colorScheme.primary);Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text("Bring your library along",fontWeight=FontWeight.SemiBold);Text("Connect YouTube to search and sync playlists",style=MaterialTheme.typography.bodySmall,color=Muted)};Icon(Icons.AutoMirrored.Rounded.ArrowForward,null)}
            }
        }
        if(recent.isNotEmpty()) {
            item {SectionTitle("Back in rotation","Your recently played songs")}
            items(recent.take(8),key={it.id}){track->SavedTrackRow(vm,track,{play(track)},{more(track)})}
        }
        if(account.connected) {
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Made for your next play",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    ActionIcon(Icons.Rounded.Refresh,"Refresh recommendations",enabled=!recommendations.loading) {
                        vm.music.refreshRecommendations(recent.firstOrNull() ?: liked.firstOrNull(),true)
                    }
                }
                if(recommendations.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(recommendations.tracks.isEmpty()&&!recommendations.loading)Text(recommendations.message ?: "Listen to a song to find more music.",color=Muted,style=MaterialTheme.typography.bodySmall)
            }
            items(recommendations.tracks.take(8),key={"suggested-${it.id}"}) { track ->
                SavedTrackRow(vm,track,{vm.play(recommendations.tracks,recommendations.tracks.indexOf(track))},{more(track)})
            }
        }
        item {SectionTitle("Find your mood")}
        item {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                listOf("Slow mornings" to "Acoustic morning music","After hours" to "Late night chill music","Turn it up" to "Workout music").forEach { (label,query)->
                    Surface(onClick={search(query)},shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.GraphicEq,null,tint=MaterialTheme.colorScheme.primary);Text(label,Modifier.weight(1f).padding(horizontal=14.dp),fontWeight=FontWeight.SemiBold);Icon(Icons.AutoMirrored.Rounded.ArrowForward,null)}
                    }
                }
            }
        }
        item {Spacer(Modifier.height(16.dp))}
    }
}

@Composable
fun CollectionTile(title: String,subtitle: String,icon: androidx.compose.ui.graphics.vector.ImageVector,modifier: Modifier=Modifier,onClick: ()->Unit) {
    Surface(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,fontWeight=FontWeight.Bold);Text(subtitle,color=Muted,style=MaterialTheme.typography.bodySmall)}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActions(vm: MuseViewModel,track: Track,dismiss: ()->Unit,playlist: ()->Unit) {
    val liked by vm.favourites.collectAsStateWithLifecycle()
    val context=LocalContext.current
    ModalBottomSheet(onDismissRequest=dismiss) {
        Column(Modifier.padding(horizontal=20.dp).padding(bottom=28.dp)) {
            TrackRow(track,{vm.play(listOf(track));dismiss()})
            HorizontalDivider(Modifier.padding(vertical=10.dp))
            ActionRow(Icons.Rounded.PlayArrow,"Play next"){vm.music.enqueue(track,true);dismiss()}
            ActionRow(Icons.AutoMirrored.Rounded.QueueMusic,"Add to queue"){vm.music.enqueue(track);dismiss()}
            ActionRow(if(track.id in liked)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,if(track.id in liked)"Remove from liked songs" else "Like song"){vm.like(track);dismiss()}
            ActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd,"Add to playlist",playlist)
            ActionRow(Icons.Rounded.Share,"Share song"){
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"${track.title}\nhttps://www.youtube.com/watch?v=${track.id}"),"Share song"));dismiss()
            }
        }
    }
}

@Composable
fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector,title: String,onClick: ()->Unit) {
    ListItem(headlineContent={Text(title)},leadingContent={Icon(icon,null,tint=MaterialTheme.colorScheme.primary)},modifier=Modifier.clickable(onClick=onClick),colors=ListItemDefaults.colors(containerColor=Color.Transparent))
}

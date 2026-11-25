package com.allen.vybe

import android.content.*
import android.os.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.allen.vybe.browser.*
import com.allen.vybe.designsystem.*
import com.allen.vybe.lyrics.*
import com.allen.vybe.ui.theme.VYBETheme
import kotlinx.coroutines.*

/** Native music-player surface around the same independently tested browser engine. */
class BrowserMusicActivity : ComponentActivity() {
    private var engine by mutableStateOf<BrowserPlaybackService?>(null)
    private var player by mutableStateOf(BrowserPlayerState())
    private val main = Handler(Looper.getMainLooper())
    private var initialQueue: ArrayList<String>? = null
    private val update = object : Runnable {
        override fun run() { engine?.let { player = it.snapshot }; main.postDelayed(this, 750) }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            engine = (binder as BrowserPlaybackService.LocalBinder).service.also { service ->
                service.playerActivity(BrowserMusicActivity::class.java)
                initialQueue?.let { service.load(it, intent.getIntExtra("index", 0), intent.getStringArrayListExtra("titles").orEmpty(), intent.getStringArrayListExtra("artists").orEmpty(), intent.getStringArrayListExtra("artistImages").orEmpty()) }
                initialQueue = null; player = service.snapshot
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { engine = null }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(savedInstanceState == null) initialQueue = intent.getStringArrayListExtra("queue")
        val target = Intent(this, BrowserPlaybackService::class.java)
        startForegroundService(target); bindService(target, connection, BIND_AUTO_CREATE); main.post(update)
        setContent { VYBETheme { Box(Modifier.fillMaxSize()) { BrowserEngineHost(engine); BrowserPlayerScreen(engine, player, ::finish) } } }
    }
    override fun onDestroy() { main.removeCallbacksAndMessages(null); unbindService(connection); super.onDestroy() }
    override fun onStart() { super.onStart(); main.removeCallbacks(update); main.post(update) }
    override fun onStop() { main.removeCallbacks(update); super.onStop() }
}

/** One retained browser attachment, independent of the native navigation destination. */
@Composable
fun BrowserEngineHost(engine: BrowserPlaybackService?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val frame = remember(context) { FrameLayout(context).apply { clipChildren = true } }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(engine, owner, frame) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if(event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && frame.windowToken != null && engine?.ownsHost(frame) == false) engine.attach(frame, context, interactive = false)
            if(event == androidx.lifecycle.Lifecycle.Event.ON_START || event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) engine?.hostVisible(frame, true)
            if(event == androidx.lifecycle.Lifecycle.Event.ON_STOP) engine?.hostVisible(frame, false)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); engine?.detach(frame) }
    }
    AndroidView(factory = { frame }, update = { container ->
        engine?.let { service ->
            if(!service.ownsHost(container)) service.attach(container, context, interactive = false)
            service.hostVisible(container, owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        }
    }, onRelease = { container -> engine?.detach(container) }, modifier = Modifier.size(1.dp).clip(androidx.compose.ui.graphics.RectangleShape))
}

@Composable
fun BrowserPlayerScreen(engine: BrowserPlaybackService?, player: BrowserPlayerState, onBack: () -> Unit, onMore: ((com.allen.vybe.domain.Track)->Unit)? = null, openQueue: (()->Unit)? = null,liked: Boolean=false,onLike: (()->Unit)?=null,onAddToPlaylist: (()->Unit)?=null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun lyricsFile(id: String) = java.io.File(context.filesDir, "lyrics-${id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,32}")) } ?: "unknown"}.lrc")
        var showQueue by remember { mutableStateOf(false) }
        var showDetails by remember { mutableStateOf(false) }
        var showTimer by remember { mutableStateOf(false) }
        var drag by remember(player.videoId) { mutableStateOf<Float?>(null) }
        var lines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
        var lyricError by remember { mutableStateOf<String?>(null) }
        var plainLyrics by remember { mutableStateOf("") }
        var lyricSource by remember { mutableStateOf("Looking up lyrics...") }
        LaunchedEffect(player.videoId, player.title, player.artist, player.duration / 1000) {
            lines = emptyList(); plainLyrics = ""; lyricError = null
            val local = withContext(Dispatchers.IO) { runCatching { lyricsFile(player.videoId).takeIf { it.exists() }?.readText()?.let(LrcLyrics::parse).orEmpty() }.getOrDefault(emptyList()) }
            if(local.isNotEmpty()) { lines = local; lyricSource = "Saved lyrics" }
            else if(player.videoId != "fixture" && player.duration > 0) {
                lyricSource = "Looking up lyrics..."; delay(1500)
                try {
                    val result = AutomaticLyrics.find(player.title, player.artist, player.duration)
                    lines = result.lines; plainLyrics = result.plain; lyricSource = result.message
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { lyricSource = "Unable to load lyrics. Check your connection." }
            }
        }
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF234936), Color(0xFF090D0B), Color(0xFF090D0B)))).safeDrawingPadding(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActionIcon(Icons.Rounded.KeyboardArrowDown, "Back to library") { onBack() }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = Muted)
                            Text("VYBE", style = MaterialTheme.typography.labelLarge)
                        }
                        ActionIcon(Icons.Rounded.MoreHoriz, "Song options") { val song=player.songs.getOrNull(player.index);if(song!=null&&onMore!=null)onMore(song.track()) else showDetails=true }
                    }
                }
                item {
                    RemoteArtwork(youtubeArtwork(player.videoId), "Song artwork", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)))
                }
                item {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(player.title,modifier=Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        if(onLike!=null)ActionIcon(if(liked)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,if(liked)"Remove current song from Liked songs" else "Like current song",tint=if(liked)MaterialTheme.colorScheme.primary else Muted,click=onLike)
                        if(onAddToPlaylist!=null)ActionIcon(Icons.AutoMirrored.Rounded.PlaylistAdd,"Add current song to playlist",click=onAddToPlaylist)
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RemoteArtwork(player.artistImage, "Artist channel image", Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape), targetPixels = 96)
                        Text(player.artist.ifBlank { "YouTube" }, color = Muted, modifier = Modifier.padding(start = 10.dp))
                    }
                    Slider(value = drag ?: player.position.toFloat().coerceIn(0f, player.duration.coerceAtLeast(1).toFloat()), onValueChange = { drag = it }, onValueChangeFinished = { drag?.let { engine?.seek(it.toLong()) }; drag = null }, valueRange = 0f..player.duration.coerceAtLeast(1).toFloat(), enabled = player.duration > 0)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(timeLabel((drag ?: player.position.toFloat()).toLong()), color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text(timeLabel(player.duration), color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionIcon(Icons.Rounded.Shuffle, if(player.shuffle)"Disable shuffle" else "Enable shuffle",tint=if(player.shuffle)MaterialTheme.colorScheme.primary else Muted) { engine?.command("shuffle") }
                        ActionIcon(Icons.Rounded.SkipPrevious, "Previous YouTube track") { engine?.command("previous") }
                        FilledIconButton(onClick = { engine?.command(if(player.playing) "pause" else "play") }, modifier = Modifier.size(72.dp)) {
                            Icon(if(player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if(player.playing) "Pause browser playback" else "Play browser playback", Modifier.size(38.dp))
                        }
                        ActionIcon(Icons.Rounded.SkipNext, "Next YouTube track", enabled = player.canNext) { engine?.command("next") }
                        ActionIcon(if(player.repeat==2)Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,"Repeat mode: ${when(player.repeat){1->"all";2->"one";else->"off"}}",tint=if(player.repeat!=0)MaterialTheme.colorScheme.primary else Muted) { engine?.command("repeat") }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={if(openQueue!=null)openQueue() else showQueue=true}){Icon(Icons.AutoMirrored.Rounded.QueueMusic,null);Spacer(Modifier.width(6.dp));Text("Queue · ${player.count}")}
                        TextButton(onClick={showTimer=true}){Icon(Icons.Rounded.Bedtime,null);Spacer(Modifier.width(6.dp));Text(if(player.sleepUntil>0)"Timer on" else "Sleep timer")}
                    }
                    player.error?.let { Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={engine?.command("play")}){Text("Try playback again")} }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF244936))) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Lyrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if(lines.isEmpty()) Text(plainLyrics.ifBlank { lyricSource }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else {
                                val active = lines.indexOfLast { it.at <= player.position }
                                val first = (active - 1).coerceAtLeast(0)
                                lines.drop(first).take(4).forEachIndexed { n, line ->
                                    Text(line.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if(first + n == active) Color.White else Color.White.copy(alpha = .4f))
                                }
                            }
                            if(lines.isNotEmpty() || plainLyrics.isNotBlank()) Text(lyricSource, style = MaterialTheme.typography.labelSmall)
                            lyricError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                item { Text("${player.index + 1} of ${player.count} · YouTube", color = Muted, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if(showQueue) AlertDialog(onDismissRequest = { showQueue = false }, title = { Text("Up next") }, text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) { itemsIndexed(player.queue) { n, title ->
                TextButton(onClick = { engine?.select(n); showQueue = false }, modifier = Modifier.fillMaxWidth()) { Text(title, color = if(n == player.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 2) }
            } }
        }, confirmButton = { TextButton(onClick = { showQueue = false }) { Text("Done") } })
        if(showDetails) AlertDialog(onDismissRequest = { showDetails = false }, title = { Text("Playback details") }, text = { Text(player.status) }, confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Done") } })
        if(showTimer)SleepTimerDialog(player.sleepUntil,{showTimer=false}){engine?.sleep(it);showTimer=false}
}


@Composable
fun ConnectedBrowserPlayerScreen(engine: BrowserPlaybackService?, onBack: () -> Unit, onMore: ((com.allen.vybe.domain.Track)->Unit)? = null, openQueue: (()->Unit)? = null,vm: MuseViewModel?=null,addToPlaylist: ((com.allen.vybe.domain.Track)->Unit)?=null) {
    val player by BrowserPlaybackService.state.collectAsStateWithLifecycle()
    val likes=vm?.favourites?.collectAsStateWithLifecycle()?.value.orEmpty()
    val song=player.songs.getOrNull(player.index)?.track()
    BrowserPlayerScreen(engine,player,onBack,onMore,openQueue,player.videoId in likes,if(vm!=null&&song!=null)({vm.like(song)})else null,if(addToPlaylist!=null&&song!=null)({addToPlaylist(song)})else null)
}

@Composable
fun ConnectedBrowserMiniPlayer(open: () -> Unit, control: (String) -> Unit,vm: MuseViewModel?=null) {
    val player by BrowserPlaybackService.state.collectAsStateWithLifecycle()
    val likes=vm?.favourites?.collectAsStateWithLifecycle()?.value.orEmpty()
    val song=player.songs.getOrNull(player.index)?.track()
    BrowserMiniPlayer(player,open,control,player.videoId in likes,if(vm!=null&&song!=null)({vm.like(song)})else null)
}

@Composable
fun BrowserMiniPlayer(state: BrowserPlayerState, open: () -> Unit, control: (String) -> Unit,liked: Boolean=false,onLike: (()->Unit)?=null) {
    Surface(onClick = open, color = Color(0xFF234936), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteArtwork(youtubeArtwork(state.videoId), "Mini-player song artwork", Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), targetPixels = 128)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(state.artist.ifBlank { "YouTube" }, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if(onLike!=null)ActionIcon(if(liked)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,if(liked)"Unlike mini-player song" else "Like mini-player song",tint=if(liked)MaterialTheme.colorScheme.primary else Muted,click=onLike)
                ActionIcon(if(state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if(state.playing) "Pause YouTube" else "Play YouTube") { control(if(state.playing) "pause" else "play") }
                ActionIcon(Icons.Rounded.SkipNext, "Next YouTube song", enabled = state.canNext) { control("next") }
            }
            if(state.duration > 0) LinearProgressIndicator(progress = { (state.position.toFloat() / state.duration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

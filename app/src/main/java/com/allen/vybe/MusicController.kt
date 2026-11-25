package com.allen.vybe

import android.content.*
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.allen.vybe.browser.*
import com.allen.vybe.data.SongEntity
import com.allen.vybe.domain.*
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

/** One application-owned connection; navigation never replaces the browser. */
class MusicController(private val app: MuseApplication) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutableEngine=MutableStateFlow<BrowserPlaybackService?>(null)
    val engine=mutableEngine.asStateFlow()
    val currentSongId=BrowserPlaybackService.state.map{it.videoId}.distinctUntilChanged().stateIn(scope,SharingStarted.Eagerly,"")
    val favourites=app.database.library().favourites().map { rows->rows.map { it.trackId }.toSet() }.stateIn(scope,SharingStarted.Eagerly,emptySet())
    private val likeLock=Mutex()
    fun like(track: Track,enabled: Boolean?=null) {
        if(!track.available||!track.id.matches(Regex("[A-Za-z0-9_-]{11}")))return
        scope.launch {
            try {likeLock.withLock {
                val saved=app.database.withTransaction {
                    val dao=app.database.library()
                    val value=enabled?:!dao.isLiked(track.id)
                    dao.saveSongs(listOf(SongEntity.from(track)))
                    if(value)dao.like(com.allen.vybe.data.FavouriteEntity(track.id)) else dao.unlike(track.id)
                    value
                }
                mutableMessage.value=if(saved)"Added to Liked songs" else "Removed from Liked songs"
            }}catch(cancelled: CancellationException){throw cancelled}
            catch(_: Exception){mutableMessage.value="Could not save this song. Try again."}
        }
    }
    private val mutableMessage=MutableStateFlow<String?>(null)
    val message=mutableMessage.asStateFlow()
    private val mutableRequestedSearch=MutableStateFlow<SearchState?>(null)
    val requestedSearch=mutableRequestedSearch.asStateFlow()
    private val mutableVoiceNavigation=MutableStateFlow(0L to "")
    val voiceNavigation=mutableVoiceNavigation.asStateFlow()
    fun handleVoice(spoken: String) { voice(spoken)?.let { mutableVoiceNavigation.value=(mutableVoiceNavigation.value.first+1) to it } }
    private var binding=false
    private val pending=mutableListOf<(BrowserPlaybackService)->Unit>()
    private var searchJob: Job?=null
    private var lastRecorded=""
    private val connection=object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName,binder: IBinder) {
            val service=(binder as BrowserPlaybackService.LocalBinder).service
            service.playerActivity(MainActivity::class.java);mutableEngine.value=service
            val work=pending.toList();pending.clear();work.forEach { it(service) }
        }
        // Android retains this binding and reconnects it after an unexpected process loss.
        override fun onServiceDisconnected(name: ComponentName) { mutableEngine.value=null }
        override fun onBindingDied(name: ComponentName) {
            releaseEngine()
            withEngine { }
        }
        override fun onNullBinding(name: ComponentName) {
            releaseEngine();pending.clear();mutableMessage.value="Could not connect playback. Try again."
        }
    }
    private fun releaseEngine() {
        mutableEngine.value=null
        if(binding){binding=false;app.unbindService(connection)}
    }
    init {
        BrowserPlaybackService.searchRequest={service,query,play -> search(query,play,service)}
        BrowserPlaybackService.taskRemoved={service ->
            searchJob?.cancel();mutableRequestedSearch.value=null;pending.clear()
            if(mutableEngine.value===service)mutableEngine.value=null
            if(binding){binding=false;app.unbindService(connection)}
        }
        BrowserPlaybackService.destroyed={service ->
            if(mutableEngine.value===service)releaseEngine()
        }
        BrowserPlaybackService.songObserved={song ->
            if(song.id!=lastRecorded&&song.id!="fixture"){
                lastRecorded=song.id
                scope.launch {
                    app.database.library().saveSongs(listOf(SongEntity.from(song.track())))
                    app.database.library().record(song.id,System.currentTimeMillis())
                }
            }
        }
    }
    fun clearMessage() { mutableMessage.value=null }
    fun disconnect() {searchJob?.cancel();mutableRequestedSearch.value=null}
    fun withEngine(action: (BrowserPlaybackService)->Unit) {
        mutableEngine.value?.let { action(it);return }
        pending+=action
        if(!binding){
            ContextCompat.startForegroundService(app,Intent(app,BrowserPlaybackService::class.java))
            binding=app.bindService(Intent(app,BrowserPlaybackService::class.java),connection,Context.BIND_AUTO_CREATE)
            if(!binding){pending.clear();mutableMessage.value="Could not start playback. Try again."}
        }
    }
    fun play(tracks: List<Track>,index: Int=0,shuffle: Boolean=false) {
        val valid=tracks.filter { it.source==Source.YOUTUBE&&it.available&&it.id.matches(Regex("[A-Za-z0-9_-]{11}")) }
        if(valid.isEmpty())return
        lastRecorded=""
        val wanted=tracks.getOrNull(index)?.id
        val ordered=if(shuffle)valid.shuffled() else valid
        scope.launch { app.database.library().saveSongs(valid.map(SongEntity::from)) }
        withEngine { service ->
            service.load(ordered.map { it.id },if(shuffle)0 else ordered.indexOfFirst { it.id==wanted }.coerceAtLeast(0),ordered.map { it.title },ordered.map { it.artist },ordered.map { it.artistArtworkUri.orEmpty() })
            if(service.snapshot.shuffle!=shuffle)service.command("shuffle")
        }
    }
    fun enqueue(track: Track,next: Boolean=false) {
        scope.launch { app.database.library().saveSongs(listOf(SongEntity.from(track))) }
        withEngine { it.enqueue(QueueSong(track.id,track.title,track.artist,track.artistArtworkUri.orEmpty()),next) }
        mutableMessage.value=if(next)"Playing next: ${track.title}" else "Added to queue"
    }
    fun command(command: String) { withEngine { it.command(command) } }
    fun enqueueAll(tracks: List<Track>) {
        val valid=QueueManager.playable(tracks).distinctBy{it.id}
        if(valid.isEmpty()){mutableMessage.value="No playable songs to add";return}
        scope.launch{app.database.library().saveSongs(valid.map(SongEntity::from))}
        withEngine { service->
            val before=service.snapshot.count
            service.enqueueAll(valid.map{QueueSong(it.id,it.title,it.artist,it.artistArtworkUri.orEmpty())})
            val added=service.snapshot.count-before
            mutableMessage.value=if(added==0)"These songs are already queued, or the 100-song queue is full" else "$added songs added to queue"
        }
    }
    fun playSearch(query: String) { withEngine { search(query,true,it) } }
    /** Navigation is returned to the visible screen; transport stays in the service. */
    fun voice(spoken: String): String? {
        val request=parseVoiceCommand(spoken)
        if(request==VoiceCommand.DisableWake){app.stopService(Intent(app,WakeVoiceService::class.java));mutableMessage.value="Hey Music is off";return null}
        if(request is VoiceCommand.Open)return request.screen
        if(request is VoiceCommand.PlaySong){playSearch(request.query);return "search"}
        if(request is VoiceCommand.Search){search(request.query,false,null);return "search"}
        if(request==VoiceCommand.Help){mutableMessage.value="Try: play a song, pause, resume, next, shuffle, repeat, sleep timer for 30 minutes, or go home.";return null}
        val service=mutableEngine.value
        if(service==null||service.snapshot.videoId.isBlank()){
            mutableMessage.value="Start a song first, or say Play followed by a song and artist.";return null
        }
        when(request){
            is VoiceCommand.Like -> service.snapshot.songs.getOrNull(service.snapshot.index)?.let{like(it.track(),request.enabled)}
            is VoiceCommand.Transport -> {
                if(request.action=="restart")service.seek(0) else service.command(request.action)
                mutableMessage.value=when(request.action){"pause"->"Paused";"play"->"Resuming playback";"next"->"Next song";"previous"->"Previous song";"restart"->"Restarting song";else->"Playback stopped"}
            }
            is VoiceCommand.Shuffle -> {
                if(request.enabled==null||service.snapshot.shuffle!=request.enabled)service.command("shuffle")
                mutableMessage.value=if(service.snapshot.shuffle)"Shuffle on" else "Shuffle off"
            }
            is VoiceCommand.Repeat -> {
                repeat(3){if(service.snapshot.repeat!=request.mode)service.command("repeat")}
                mutableMessage.value=when(request.mode){1->"Repeat all";2->"Repeat this song";else->"Repeat off"}
            }
            is VoiceCommand.SeekBy -> service.seek(service.snapshot.position+request.milliseconds)
            is VoiceCommand.Timer -> {service.sleep(request.minutes);mutableMessage.value=if(request.minutes==0)"Sleep timer cancelled" else "Sleep timer set for ${request.minutes} minutes"}
            else -> Unit
        }
        return null
    }
    private fun search(query: String,play: Boolean,service: BrowserPlaybackService?) {
        searchJob?.cancel();searchJob=scope.launch {
            mutableRequestedSearch.value=SearchState(query,loading=true,submitted=true)
            service?.reportError(null)
            try {
                if(!app.youtube.connected){
                    val grant=Identity.getAuthorizationClient(app).authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/youtube.force-ssl"))).build()).await()
                    check(!grant.hasResolution()&&!grant.accessToken.isNullOrBlank()) { "Connect YouTube in VYBE Settings to search for songs." }
                    app.youtube.connect(grant.accessToken!!)
                }
                val page=app.youtube.search(query)
                val results=page.tracks.filter { it.available }
                check(results.isNotEmpty()) { "No songs found for $query. Try another title or artist." }
                app.preferences.recordSearch(query);app.database.library().saveSongs(results.map(SongEntity::from))
                mutableRequestedSearch.value=SearchState(query,submitted=true,tracks=results,channels=page.channels,next=page.next)
                if(play)this@MusicController.play(results) else service?.prepareSongs(results.map { QueueSong(it.id,it.title,it.artist,it.artistArtworkUri.orEmpty()) })
            } catch(cancelled: CancellationException){throw cancelled}
            catch(error: Exception){ val message=error.message?:"Could not find that song. Try again.";mutableRequestedSearch.value=SearchState(query,submitted=true,error=message);service?.reportError(message);mutableMessage.value=message }
        }
    }
}

fun QueueSong.track()=Track(id,title,artist,Source.YOUTUBE,artworkUri=youtubeArtwork(id),artistArtworkUri=artistImage.ifBlank { null })

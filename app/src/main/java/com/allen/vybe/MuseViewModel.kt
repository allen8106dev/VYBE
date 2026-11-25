package com.allen.vybe

import android.app.Application
import android.app.PendingIntent
import android.os.SystemClock
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.allen.vybe.auth.authorizationErrorMessage
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allen.vybe.catalog.*
import com.allen.vybe.data.*
import com.allen.vybe.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

 data class SearchState(val query: String = "", val loading: Boolean = false, val submitted: Boolean = false, val tracks: List<Track> = emptyList(), val channels: List<Channel> = emptyList(), val next: String? = null, val error: String? = null)
data class PlaylistState(val playlist: MusicPlaylist? = null, val tracks: List<Track> = emptyList(), val loading: Boolean = false, val error: String? = null)
data class AccountState(val connected: Boolean = false, val loading: Boolean = false, val error: String? = null)

class MuseViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MuseApplication
    private val dao = app.database.library()
    val music = app.music
    val playlists = combine(dao.observePlaylists(), dao.observeEntries()) { lists, entries -> lists.map { p -> MusicPlaylist(p.id, p.title, p.description, entries.count { it.playlistId == p.id }) } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favourites = music.favourites
    val history = dao.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val likedSongs=dao.likedSongs().map { rows -> rows.map { it.track() } }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val recentSongs=dao.recentSongs().map { rows -> rows.map { it.track() } }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val artists = dao.artists().map { rows -> rows.map { it.name }.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val recentSearches = app.preferences.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reducedMotion = app.preferences.reducedMotion.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val mutableSearch = MutableStateFlow(SearchState()); val search = mutableSearch.asStateFlow()
    private val mutablePlaylist = MutableStateFlow(PlaylistState()); val selected = mutablePlaylist.asStateFlow()
    private val mutableAccount = MutableStateFlow(AccountState()); val account = mutableAccount.asStateFlow()
    private val mutableCloud = MutableStateFlow<List<MusicPlaylist>>(emptyList()); val cloud = mutableCloud.asStateFlow()
    private val mutableBusy = MutableStateFlow(false); val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null); val message = mutableMessage.asStateFlow()
    private val pendingAuthorization = MutableStateFlow<PendingIntent?>(null)
    val authorization = pendingAuthorization.asStateFlow()
    private var authorizationJob: Job? = null
    fun authorizationLaunched() { pendingAuthorization.value = null }
    fun authorize() {
        if (mutableAccount.value.loading) return
        authorizing()
        authorizationJob = viewModelScope.launch {
            try {
                val client = Identity.getAuthorizationClient(app)
                app.youtube.takeInvalidToken()?.let { client.clearToken(ClearTokenRequest.builder().setToken(it).build()).await() }
                // Android OAuth client is resolved by package + signing SHA-1 in Cloud Console.
                // No client ID / Web client is used unless a backend offline-code flow is added.
                val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/youtube.force-ssl"))).build()
                val result = client.authorize(request).await()
                if (result.hasResolution()) {
                    pendingAuthorization.value = requireNotNull(result.pendingIntent)
                } else connect(result.accessToken)
            } catch (e: Exception) { if (e is CancellationException) throw e; authFailed(e) }
        }
    }
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var musicSearchGeneration = 0L
    private var lastLiveSearchAt = 0L
    private val searchCache = linkedMapOf<String, SearchPage>()
    private var playlistJob: Job? = null
    private var cloudJob: Job? = null
    private var operationJob: Job? = null
    fun clearMessage() { mutableMessage.value = null }
    fun notify(text: String) { mutableMessage.value = text }
    fun authorizing() { mutableAccount.value = AccountState(loading = true) }
    fun authFailed(cause: Throwable? = null) { mutableAccount.value = AccountState(error = authorizationErrorMessage((cause as? ApiException)?.statusCode)) }
    fun connect(token: String?) {
        if (token.isNullOrBlank()) { authFailed(); return }
        searchGeneration++; searchJob?.cancel(); searchCache.clear()
        app.youtube.connect(token); mutableAccount.value = AccountState(connected = true); refreshCloud()
    }
    fun disconnect(): String? {
        music.disconnect()
        authorizationJob?.cancel(); pendingAuthorization.value = null
        searchGeneration++; searchJob?.cancel(); playlistJob?.cancel(); cloudJob?.cancel(); operationJob?.cancel()
        searchCache.clear()
        val token = app.youtube.disconnect()
        mutableAccount.value = AccountState(); mutableCloud.value = emptyList()
        mutableSearch.value = SearchState(); mutableBusy.value = false
        if (mutablePlaylist.value.playlist?.cloud == true) mutablePlaylist.value = PlaylistState()
        return token
    }
    private fun error(error: Exception): String {
        if (error is CancellationException) throw error
        if (!app.youtube.connected && mutableAccount.value.connected) {
            mutableAccount.value = AccountState(error = "Authorization expired. Connect YouTube again.")
            mutableCloud.value = emptyList()
            if (mutablePlaylist.value.playlist?.cloud == true) mutablePlaylist.value = PlaylistState()
            mutableSearch.value = SearchState()
        }
        return when(error) {
            is java.io.IOException -> "Network unavailable. Check your connection and retry."
            else -> error.message ?: "The operation failed. Please retry."
        }
    }
    fun refreshCloud() {
        if (!app.youtube.connected) return
        cloudJob?.cancel(); cloudJob = viewModelScope.launch {
            mutableAccount.value = AccountState(true, true)
            try { mutableCloud.value = app.youtube.playlists(); mutableAccount.value = AccountState(true) }
            catch (e: Exception) { val message = error(e); mutableAccount.value = mutableAccount.value.copy(loading = false, error = message) }
        }
    }
    fun search(query: String, more: Boolean = false) {
        val term = query.trim().take(200); if (term.isBlank()) return
        val previous = mutableSearch.value
        val page = if (more) previous.next ?: return else null
        val cached = if (more) null else searchCache[term]
        val request = ++searchGeneration
        searchJob?.cancel(); searchJob = viewModelScope.launch {
            mutableSearch.value = if (more) previous.copy(loading = true, error = null) else SearchState(term, loading = true, submitted = true)
            try {
                if (request != searchGeneration) return@launch
                if (cached == null && !more) lastLiveSearchAt = SystemClock.elapsedRealtime()
                val result = cached ?: app.youtube.search(term, page)
                if (request != searchGeneration) return@launch
                if (!more && cached == null) cacheSearch(term, result)
                mutableSearch.value = SearchState(term, submitted = true, tracks = if (more) (previous.tracks + result.tracks).distinctBy { it.id } else result.tracks,
                    channels = if (more) (previous.channels + result.channels).distinctBy { it.id } else result.channels, next = result.next)
            } catch (e: Exception) {
                if (request != searchGeneration) return@launch
                val message = error(e); mutableSearch.value = mutableSearch.value.copy(loading = false, error = message, submitted = true)
            }
        }
    }
    fun searchAsYouType(query: String) {
        val raw = query.take(200)
        val term = raw.trim()
        val request = ++searchGeneration
        searchJob?.cancel()
        if (term.length < 2 || !app.youtube.connected) {
            if (!app.youtube.connected) searchCache.clear()
            mutableSearch.value = SearchState(query = raw)
            return
        }
        searchCache[term]?.let { cached ->
            mutableSearch.value = SearchState(query = raw, tracks = cached.tracks, channels = cached.channels, next = cached.next)
            return
        }
        searchJob = viewModelScope.launch {
            mutableSearch.value = SearchState(query = raw, loading = true)
            val throttle = if (lastLiveSearchAt == 0L) 0L else (2500L - (SystemClock.elapsedRealtime() - lastLiveSearchAt)).coerceAtLeast(0L)
            delay(maxOf(500L, throttle))
            if (request != searchGeneration) return@launch
            try {
                lastLiveSearchAt = SystemClock.elapsedRealtime()
                val result = app.youtube.search(term)
                if (request != searchGeneration) return@launch
                cacheSearch(term, result)
                mutableSearch.value = SearchState(query = raw, tracks = result.tracks, channels = result.channels, next = result.next)
            } catch (e: Exception) {
                if (request != searchGeneration) return@launch
                val message = error(e)
                mutableSearch.value = SearchState(query = raw, error = message)
            }
        }
    }
    private fun cacheSearch(query: String, result: SearchPage) {
        searchCache[query] = result
        while (searchCache.size > 8) searchCache.remove(searchCache.keys.first())
    }
    fun recordRecentSearch(track: Track) { viewModelScope.launch { app.preferences.recordSearch(track) } }
    fun removeRecentSearch(id: String) { viewModelScope.launch { app.preferences.removeRecentSearch(id) } }
    fun clearRecentSearches() { viewModelScope.launch { app.preferences.clearSearches() } }
    fun resetSearch() { searchGeneration++; searchJob?.cancel(); mutableSearch.value = SearchState() }
    fun select(id: String, cloud: Boolean) {
        playlistJob?.cancel()
        playlistJob = viewModelScope.launch {
            val cached = (if (cloud) mutableCloud.value else playlists.value).find { it.id == id }
            mutablePlaylist.value = PlaylistState(cached, loading = true)
            try {
                val playlist = cached ?: repository(cloud).playlists().find { it.id == id }
                checkNotNull(playlist) { "This playlist is no longer available. Refresh your library." }
                mutablePlaylist.value = PlaylistState(playlist, repository(cloud).contents(id))
            } catch (e: Exception) {
                val message = error(e)
                mutablePlaylist.value = PlaylistState(cached, error = message)
            }
        }
    }
    private fun repository(cloud: Boolean): PlaylistRepository = if (cloud) app.youtube else app.localPlaylists
    private fun action(onError: ((String)->Unit)?=null,block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        operationJob = viewModelScope.launch {
            try { block() }
            catch (e: Exception) { val message=error(e);notify(message);onError?.invoke(message) }
            finally { mutableBusy.value = false }
        }
    }
    fun create(title: String, cloud: Boolean) = action {
        repository(cloud).create(title)
        if (cloud) refreshCloud()
        notify(if (cloud) "Private playlist created on YouTube." else "Local playlist created.")
    }
    fun createAndAdd(title: String,track: Track) = action {
        val id=app.localPlaylists.create(title);app.localPlaylists.add(id,track);notify("Playlist created and song added.")
    }
    fun rename(playlist: MusicPlaylist,title: String) = edit(playlist,title,playlist.description)
    fun edit(playlist: MusicPlaylist,title: String,description: String,onSaved: ()->Unit={},onError: (String)->Unit={}) = action(onError) {
        val name=validName(title)
        require(description.length<=5000){"Use a description under 5000 characters."}
        repository(playlist.cloud).rename(playlist.id,name,description)
        mutablePlaylist.update { if(it.playlist?.id==playlist.id&&it.playlist.cloud==playlist.cloud)it.copy(playlist=playlist.copy(title=name,description=description)) else it }
        if(playlist.cloud)refreshCloud()
        notify("Playlist updated");onSaved()
    }
    fun delete(playlist: MusicPlaylist,onDeleted: ()->Unit={},onError: (String)->Unit={}) = action(onError) {
        repository(playlist.cloud).delete(playlist.id)
        if(mutablePlaylist.value.playlist?.let{it.id==playlist.id&&it.cloud==playlist.cloud}==true)mutablePlaylist.value=PlaylistState()
        if(playlist.cloud)refreshCloud()
        notify("Playlist deleted");onDeleted()
    }
    fun duplicate(playlist: MusicPlaylist) = action {
        val tracks=repository(playlist.cloud).contents(playlist.id)
        app.localPlaylists.copy((playlist.title.take(143)+" (copy)"),playlist.description,tracks)
        notify("Copy saved on this phone")
    }
    fun sharePlaylist(playlist: MusicPlaylist,share: (String)->Unit) = action {
        val text=if(playlist.cloud)"${playlist.title}\nhttps://www.youtube.com/playlist?list=${playlist.id}"
        else {
            val tracks=app.localPlaylists.contents(playlist.id)
            buildString {
                append(playlist.title)
                if(playlist.description.isNotBlank())append("\n${playlist.description}")
                tracks.take(100).forEach{append("\n\n${it.title} - ${it.artist}\nhttps://www.youtube.com/watch?v=${it.id}")}
                if(tracks.size>100)append("\n\nFirst 100 songs of ${tracks.size}")
            }
        }
        share(text)
    }
    fun queuePlaylist(playlist: MusicPlaylist) = action {
        music.enqueueAll(repository(playlist.cloud).contents(playlist.id))
    }
    fun add(playlist: MusicPlaylist, track: Track) = action {
        repository(playlist.cloud).add(playlist.id, track)
        if (playlist.cloud) refreshCloud()
        if (mutablePlaylist.value.playlist?.id == playlist.id) select(playlist.id, playlist.cloud)
        notify("Added to ${playlist.title}.")
    }
    fun remove(playlist: MusicPlaylist, track: Track) = action {
        repository(playlist.cloud).remove(playlist.id, track)
        select(playlist.id, playlist.cloud); if (playlist.cloud) refreshCloud()
    }
    fun move(playlist: MusicPlaylist, track: Track, index: Int) = action {
        repository(playlist.cloud).move(playlist.id, track, index)
        select(playlist.id, playlist.cloud)
    }
    fun like(track: Track) {music.like(track)}
    fun follow(name: String) = action { if (name in artists.value) dao.unfollow(name) else dao.follow(ArtistEntity(name)) }
    fun clearHistory() = action { dao.clearHistory(); app.preferences.clearSearches(); music.clearRecommendations(); notify("Listening and search history cleared.") }
    fun reducedMotion(value: Boolean) { viewModelScope.launch { app.preferences.reduceMotion(value) } }
    fun play(tracks: List<Track>, index: Int = 0, shuffle: Boolean = false) { music.play(tracks,index,shuffle) }
    init {
        viewModelScope.launch { music.requestedSearch.filterNotNull().collect { result ->
            if(result.loading){searchGeneration++;musicSearchGeneration=searchGeneration;searchJob?.cancel();lastLiveSearchAt=SystemClock.elapsedRealtime()}
            if(result.loading||musicSearchGeneration==searchGeneration)mutableSearch.value=result
            if(app.youtube.connected&&!mutableAccount.value.connected)mutableAccount.value=AccountState(connected=true)
        } }
        authorizationJob=viewModelScope.launch {
            try {
                val result=Identity.getAuthorizationClient(app).authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/youtube.force-ssl"))).build()).await()
                if(!result.hasResolution()&&!result.accessToken.isNullOrBlank())connect(result.accessToken)
            }catch(cancelled: CancellationException){throw cancelled}
            catch(_: Exception){ /* Reconnection stays an explicit account action if no saved grant exists. */ }
        }
    }
}

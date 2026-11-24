package com.allen.vybe.domain

import kotlinx.coroutines.flow.Flow

enum class Source { YOUTUBE }
data class Track(
    val id: String, val title: String, val artist: String,
    val source: Source = Source.YOUTUBE,
    val mediaUri: String? = null, val artworkUri: String? = null,
    val palette: Int = 0, val itemId: String? = null,
    val available: Boolean = true, val channelId: String? = null, val artistArtworkUri: String? = null
)
data class MusicPlaylist(val id: String, val title: String, val description: String = "", val count: Int = 0, val cloud: Boolean = false)
data class SearchPage(val tracks: List<Track>, val channels: List<Channel> = emptyList(), val next: String? = null)
data class Channel(val id: String, val title: String)
interface MusicCatalogProvider { suspend fun search(query: String, page: String? = null): SearchPage }
interface MusicSearchService : MusicCatalogProvider
interface PlaylistRepository {
    suspend fun playlists(): List<MusicPlaylist>
    suspend fun contents(id: String): List<Track>
    suspend fun create(title: String): String
    suspend fun rename(id: String, title: String, description: String)
    suspend fun delete(id: String)
    suspend fun add(id: String, track: Track)
    suspend fun remove(id: String, track: Track)
    suspend fun move(id: String, track: Track, position: Int)
}
interface RecommendationService { fun suggestions(): List<Track> }
interface PlaybackEngine {
    val state: kotlinx.coroutines.flow.StateFlow<PlaybackSnapshot>
    fun play(tracks: List<Track>, index: Int = 0)
    fun toggle()
    fun seek(position: Long)
    fun next()
    fun previous()
}
data class PlaybackSnapshot(
    val connected: Boolean = false, val track: Track? = null,
    val playing: Boolean = false, val buffering: Boolean = false,
    val position: Long = 0, val duration: Long = 0,
    val queue: List<Track> = emptyList(), val index: Int = -1,
    val shuffle: Boolean = false, val repeat: Int = 0, val error: String? = null
)
/** Validates operations; Media3 remains the single owner of queue state. */
object QueueManager {
    fun canMove(from: Int, to: Int, size: Int) = from in 0 until size && to in 0 until size && from != to
    fun playable(tracks: List<Track>) = tracks.filter { it.source == Source.YOUTUBE && it.available && it.id.matches(Regex("[A-Za-z0-9_-]{11}")) }
}
fun validName(name: String): String = name.trim().also { require(it.isNotEmpty() && it.length <= 150) { "Use a name between 1 and 150 characters." } }

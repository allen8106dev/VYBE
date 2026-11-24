package com.allen.vybe.library

import androidx.room.withTransaction
import com.allen.vybe.data.*
import com.allen.vybe.domain.*
import java.util.UUID

class LocalPlaylistRepository(private val db: MuseDatabase) : PlaylistRepository {
    private val dao = db.library()
    override suspend fun playlists() = dao.playlists().map { MusicPlaylist(it.id, it.title, it.description, dao.entries(it.id).size) }
    override suspend fun contents(id: String): List<Track> {
        val entries=dao.entries(id);val songs=dao.songs(entries.map { it.trackId }).associateBy { it.id }
        return entries.mapNotNull { songs[it.trackId]?.track() }
    }
    override suspend fun create(title: String): String = UUID.randomUUID().toString().also { dao.create(PlaylistEntity(it, validName(title))) }
    suspend fun copy(title: String,description: String,tracks: List<Track>): String = db.withTransaction {
        val id=UUID.randomUUID().toString()
        val songs=QueueManager.playable(tracks).distinctBy { it.id }
        dao.create(PlaylistEntity(id,validName(title),description.take(5000)))
        dao.saveSongs(songs.map(SongEntity::from))
        songs.forEachIndexed { n,track->dao.insert(EntryEntity(id,track.id,n)) }
        id
    }
    override suspend fun rename(id: String, title: String, description: String) = dao.rename(id, validName(title), description)
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun add(id: String, track: Track) {
        require(track.available && track.id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "This song is unavailable." }
        db.withTransaction { dao.saveSongs(listOf(SongEntity.from(track)));dao.insert(EntryEntity(id, track.id, (dao.entries(id).maxOfOrNull { it.position } ?: -1) + 1)) }
    }
    override suspend fun remove(id: String, track: Track) = dao.remove(id, track.id)
    override suspend fun move(id: String, track: Track, position: Int) {
        db.withTransaction {
            val entries = dao.entries(id).toMutableList()
            val from = entries.indexOfFirst { it.trackId == track.id }
            if (!QueueManager.canMove(from, position, entries.size)) return@withTransaction
            entries.add(position, entries.removeAt(from))
            entries.forEachIndexed { index, entry -> dao.position(id, entry.trackId, index) }
        }
    }
}

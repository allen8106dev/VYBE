package com.allen.vybe.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.allen.vybe.domain.Track
import com.allen.vybe.domain.Source
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "songs")
data class SongEntity(@PrimaryKey val id: String, val title: String, val artist: String, val artwork: String?, val channelId: String?, val artistArtwork: String?) {
    fun track() = Track(id, title, artist, Source.YOUTUBE, artworkUri = artwork, channelId = channelId, artistArtworkUri = artistArtwork)
    companion object { fun from(t: Track) = SongEntity(t.id,t.title,t.artist,t.artworkUri,t.channelId,t.artistArtworkUri) }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey val id: String, val title: String, val description: String = "")
@Entity(tableName = "entries", primaryKeys = ["playlistId", "trackId"], foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)], indices = [Index("playlistId")])
data class EntryEntity(val playlistId: String, val trackId: String, val position: Int)
@Entity(tableName = "favourites")
data class FavouriteEntity(@PrimaryKey val trackId: String)
@Entity(tableName = "history")
data class HistoryEntity(@PrimaryKey val trackId: String, val lastPlayed: Long, val playCount: Int)
@Entity(tableName = "artists")
data class ArtistEntity(@PrimaryKey val name: String)
@Dao
interface LibraryDao {
    @Upsert suspend fun saveSongs(songs: List<SongEntity>)
    @Query("SELECT * FROM songs WHERE id IN (:ids)") suspend fun songs(ids: List<String>): List<SongEntity>
    @Query("SELECT songs.* FROM songs INNER JOIN favourites ON songs.id=favourites.trackId ORDER BY songs.title COLLATE NOCASE") fun likedSongs(): Flow<List<SongEntity>>
    @Query("SELECT songs.* FROM songs INNER JOIN history ON songs.id=history.trackId ORDER BY history.lastPlayed DESC LIMIT 50") fun recentSongs(): Flow<List<SongEntity>>
    @Query("SELECT * FROM playlists ORDER BY title COLLATE NOCASE") fun observePlaylists(): Flow<List<PlaylistEntity>>
    @Query("SELECT * FROM playlists ORDER BY title COLLATE NOCASE") suspend fun playlists(): List<PlaylistEntity>
    @Insert suspend fun create(playlist: PlaylistEntity)
    @Query("UPDATE playlists SET title = :title, description = :description WHERE id = :id") suspend fun rename(id: String, title: String, description: String)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM entries ORDER BY position") fun observeEntries(): Flow<List<EntryEntity>>
    @Query("SELECT * FROM entries WHERE playlistId = :id ORDER BY position") suspend fun entries(id: String): List<EntryEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: EntryEntity)
    @Query("DELETE FROM entries WHERE playlistId = :id AND trackId = :trackId") suspend fun remove(id: String, trackId: String)
    @Query("UPDATE entries SET position = :position WHERE playlistId = :id AND trackId = :trackId") suspend fun position(id: String, trackId: String, position: Int)
    @Query("SELECT * FROM favourites") fun favourites(): Flow<List<FavouriteEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE trackId=:id)") suspend fun isLiked(id: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun like(favourite: FavouriteEntity)
    @Query("DELETE FROM favourites WHERE trackId = :id") suspend fun unlike(id: String)
    @Query("SELECT * FROM history ORDER BY lastPlayed DESC LIMIT 50") fun history(): Flow<List<HistoryEntity>>
    @Query("INSERT INTO history(trackId,lastPlayed,playCount) VALUES(:id,:at,1) ON CONFLICT(trackId) DO UPDATE SET lastPlayed=:at, playCount=playCount+1") suspend fun record(id: String, at: Long)
    @Query("SELECT * FROM artists") fun artists(): Flow<List<ArtistEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun follow(artist: ArtistEntity)
    @Query("DELETE FROM artists WHERE name = :name") suspend fun unfollow(name: String)
    @Query("DELETE FROM history") suspend fun clearHistory()
}
@Database(entities = [PlaylistEntity::class, EntryEntity::class, FavouriteEntity::class, HistoryEntity::class, ArtistEntity::class, SongEntity::class], version = 2, exportSchema = true)
abstract class MuseDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS songs (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, artwork TEXT, channelId TEXT, artistArtwork TEXT)")
                listOf("entries","favourites","history").forEach { db.execSQL("DELETE FROM $it WHERE trackId IN ('muse-dawn','muse-orbit','muse-tide')") }
            }
        }
    }
}

package com.allen.vybe.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.allen.vybe.domain.Source
import com.allen.vybe.domain.Track
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.musePreferences by preferencesDataStore("muse")
class MusePreferences(context: Context) {
    private val store = context.applicationContext.musePreferences
    private val motion = booleanPreferencesKey("reduced_motion")
    private val searches = stringPreferencesKey("recent_searches")
    private val recentSearchTracks = stringPreferencesKey("recent_search_tracks")
    private val autoplayKey = booleanPreferencesKey("autoplay_suggestions")
    val autoplay = store.data.map { it[autoplayKey] ?: true }
    suspend fun autoplay(value: Boolean) { store.edit { it[autoplayKey] = value } }
    val reducedMotion = store.data.map { it[motion] ?: false }
    val recentSearches = store.data.map { decodeRecentTracks(it[recentSearchTracks]) }
    suspend fun reduceMotion(value: Boolean) { store.edit { it[motion] = value } }
    suspend fun recordSearch(track: Track) {
        if (!track.available || !track.id.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        store.edit { prefs ->
            val recent = decodeRecentTracks(prefs[recentSearchTracks])
            prefs[recentSearchTracks] = encodeRecentTracks((listOf(track) + recent.filterNot { it.id == track.id }).take(12))
            prefs.remove(searches)
        }
    }
    suspend fun removeRecentSearch(id: String) {
        store.edit { prefs ->
            val recent = decodeRecentTracks(prefs[recentSearchTracks]).filterNot { it.id == id }
            if (recent.isEmpty()) prefs.remove(recentSearchTracks) else prefs[recentSearchTracks] = encodeRecentTracks(recent)
        }
    }
    suspend fun clearSearches() { store.edit {
        it.remove(searches)
        it.remove(recentSearchTracks)
    } }

    private fun encodeRecentTracks(tracks: List<Track>) = JSONArray().apply {
        tracks.forEach { track ->
            put(JSONObject()
                .put("id", track.id)
                .put("title", track.title.take(200))
                .put("artist", track.artist.take(150))
                .put("artwork", track.artworkUri?.take(2048) ?: JSONObject.NULL)
                .put("artistArtwork", track.artistArtworkUri?.take(2048) ?: JSONObject.NULL))
        }
    }.toString()

    private fun decodeRecentTracks(raw: String?): List<Track> = try {
        if (raw.isNullOrBlank()) emptyList() else JSONArray(raw).let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val song = array.optJSONObject(index) ?: continue
                    val id = song.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: continue
                    val title = song.optString("title").take(200)
                    if (title.isBlank()) continue
                    add(Track(id, title, song.optString("artist").take(150), Source.YOUTUBE,
                        artworkUri = song.optString("artwork").takeUnless { it.isBlank() || it == "null" },
                        artistArtworkUri = song.optString("artistArtwork").takeUnless { it.isBlank() || it == "null" }))
                }
            }.distinctBy { it.id }.take(12)
        }
    } catch (_: Exception) { emptyList() }
}

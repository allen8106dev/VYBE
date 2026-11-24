package com.allen.vybe.catalog

import android.text.Html
import com.allen.vybe.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

class YouTubeException(val status: Int, val reason: String, val diagnostic: String? = null) :
    Exception(apiErrorMessage(status, reason) + (diagnostic?.let { " [$it]" } ?: ""))
fun apiErrorMessage(status: Int, reason: String): String = when {
    status == 401 -> "Your Google authorization expired. Connect YouTube again."
    reason in setOf("quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded") || status == 429 -> "YouTube API quota is exhausted. Try later; repeated retries use more quota."
    reason in setOf("channelNotFound", "youtubeSignupRequired") -> "YouTube could not find a channel for the connected account. Open YouTube with that same Google account, finish channel setup if prompted, then reconnect in Muse."
    reason == "playlistNotFound" -> "This playlist is no longer available. Refresh your library."
    reason in setOf("videoNotFound", "playlistItemNotFound") -> "This playlist item or video is no longer available. Refresh your library."
    status == 403 -> "YouTube denied this action. Check API access, playlist ownership and permissions."
    status == 404 -> "YouTube could not find the requested resource (HTTP 404)."
    status in 500..599 -> "YouTube is unavailable. Try again later."
    else -> "YouTube could not complete this action ($status)."
}
class YouTubeRepository : MusicCatalogProvider, MusicSearchService, PlaylistRepository {
    @Volatile private var token: String? = null
    @Volatile private var generation = 0
    @Volatile private var invalidToken: String? = null
    fun takeInvalidToken(): String? = invalidToken.also { invalidToken = null }
    val connected get() = token != null
    fun connect(accessToken: String) { require(accessToken.isNotBlank()); generation++; token = accessToken }
    fun disconnect(): String? { generation++; val previous = token ?: invalidToken; token = null; invalidToken = null; return previous }
    private suspend fun request(path: String, params: Map<String, String> = emptyMap(), method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val activeToken = token ?: throw IllegalStateException("Connect your YouTube account in Settings first.")
        val requestGeneration = generation
        val query = params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val connection = URI("https://www.googleapis.com/youtube/v3/$path?$query").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000; connection.readTimeout = 15000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Authorization", "Bearer $activeToken")
        connection.setRequestProperty("Accept", "application/json")
        try {
            coroutineContext.ensureActive()
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { reader ->
                val buffer = CharArray(8192); val result = StringBuilder()
                while (true) { val read = reader.read(buffer); if (read < 0) break; require(result.length + read <= 4_000_000) { "YouTube response was too large." }; result.append(buffer, 0, read) }
                result.toString()
            }.orEmpty()
            coroutineContext.ensureActive()
            check(requestGeneration == generation) { "The connected account changed. Please retry." }
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) {
                val reason = json.optJSONObject("error")?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
                if (status == 401) { invalidToken = activeToken; token = null }
                // Only fixed endpoint names and machine-readable reason codes are diagnostic data.
                // Never include response bodies, request parameters, tokens or account details.
                val safeReason = reason.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,79}")) } ?: "unspecified"
                throw YouTubeException(status, reason, "$method $path: HTTP $status, $safeReason")
            }
            json
        } finally { connection.disconnect() }
    }
    override suspend fun search(query: String, page: String?): SearchPage {
        require(query.isNotBlank()) { "Enter a search term." }
        val params = mutableMapOf("part" to "snippet", "q" to query.take(200), "type" to "video,channel", "maxResults" to "20")
        if (page != null) params["pageToken"] = page
        val json = request("search", params)
        val tracks = mutableListOf<Track>(); val channels = mutableListOf<Channel>()
        json.optJSONArray("items").objects().forEach { item ->
            val id = item.getJSONObject("id"); val snippet = item.getJSONObject("snippet")
            if (id.has("videoId")) tracks += Track(id.getString("videoId"), clean(snippet.optString("title")), clean(snippet.optString("channelTitle")), Source.YOUTUBE,
                artworkUri = thumbnail(snippet), channelId = snippet.optString("channelId"))
            if (id.has("channelId")) channels += Channel(id.getString("channelId"), clean(snippet.optString("title")))
        }
        return SearchPage(withChannelImages(tracks), channels, json.optString("nextPageToken").ifBlank { null })
    }
    private fun thumbnail(snippet: JSONObject): String? = snippet.optJSONObject("thumbnails")?.let {
        (it.optJSONObject("high") ?: it.optJSONObject("medium") ?: it.optJSONObject("default"))?.optString("url")?.takeIf { url -> url.startsWith("https://") }
    }
    private suspend fun withChannelImages(tracks: List<Track>): List<Track> {
        val images = mutableMapOf<String, String?>()
        try {
            tracks.mapNotNull { it.channelId?.takeIf(String::isNotBlank) }.distinct().chunked(50).forEach { ids ->
                request("channels", mapOf("part" to "snippet", "id" to ids.joinToString(","))).optJSONArray("items").objects().forEach {
                    images[it.getString("id")] = thumbnail(it.getJSONObject("snippet"))
                }
            }
        } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(_: Exception) { /* An unavailable creator image must not discard playable search results. */ }
        return tracks.map { it.copy(artistArtworkUri = images[it.channelId]) }
    }
    private suspend fun all(path: String, params: Map<String, String>): List<JSONObject> {
        val results = mutableListOf<JSONObject>(); var page: String? = null
        val seen = mutableSetOf<String>()
        do {
            val query = params.toMutableMap().apply { put("maxResults", "50"); page?.let { put("pageToken", it) } }
            val json = request(path, query)
            results += json.optJSONArray("items").objects()
            page = json.optString("nextPageToken").ifBlank { null }
            if (page != null) check(seen.add(page)) { "YouTube returned a repeated page token. Try refreshing." }
        } while (page != null)
        return results
    }
    override suspend fun playlists() = all("playlists", mapOf("part" to "snippet,contentDetails", "mine" to "true")).map {
        val s = it.getJSONObject("snippet")
        MusicPlaylist(it.getString("id"), clean(s.getString("title")), s.optString("description"), it.optJSONObject("contentDetails")?.optInt("itemCount") ?: 0, true)
    }
    override suspend fun contents(id: String) = withChannelImages(all("playlistItems", mapOf("part" to "snippet,contentDetails,status", "playlistId" to id)).map {
        val s = it.getJSONObject("snippet"); val video = s.optJSONObject("resourceId")?.optString("videoId").orEmpty()
        val title = clean(s.optString("title", "Unavailable video"))
        Track(video, title, clean(s.optString("videoOwnerChannelTitle", s.optString("channelTitle"))), Source.YOUTUBE,
            artworkUri = thumbnail(s), itemId = it.getString("id"), available = video.isNotBlank() && title !in setOf("Deleted video", "Private video"), channelId = s.optString("videoOwnerChannelId"))
    })
    override suspend fun create(title: String): String = request("playlists", mapOf("part" to "snippet,status"), "POST",
        JSONObject().put("snippet", JSONObject().put("title", validName(title))).put("status", JSONObject().put("privacyStatus", "private"))).getString("id")
    override suspend fun rename(id: String, title: String, description: String) {
        request("playlists", mapOf("part" to "snippet"), "PUT", JSONObject().put("id", id).put("snippet", JSONObject().put("title", validName(title)).put("description", description)))
    }
    override suspend fun delete(id: String) { request("playlists", mapOf("id" to id), "DELETE") }
    override suspend fun add(id: String, track: Track) {
        require(track.id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "This song is unavailable." }
        // Refresh first: duplicate canonical video IDs are deliberately rejected by Muse.
        require(contents(id).none { it.id == track.id }) { "This video is already in the playlist." }
        request("playlistItems", mapOf("part" to "snippet"), "POST", JSONObject().put("snippet", JSONObject().put("playlistId", id).put("resourceId", resource(track.id))))
    }
    override suspend fun remove(id: String, track: Track) {
        request("playlistItems", mapOf("id" to requireNotNull(track.itemId) { "Refresh the playlist before removing this item." }), "DELETE")
    }
    override suspend fun move(id: String, track: Track, position: Int) {
        require(position >= 0)
        request("playlistItems", mapOf("part" to "snippet"), "PUT", JSONObject().put("id", requireNotNull(track.itemId)).put("snippet", JSONObject().put("playlistId", id).put("resourceId", resource(track.id)).put("position", position)))
    }
    private fun resource(video: String) = JSONObject().put("kind", "youtube#video").put("videoId", video)
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun clean(value: String) = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}

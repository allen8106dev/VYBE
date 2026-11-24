package com.allen.vybe.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

data class LyricsResult(val lines: List<LyricLine> = emptyList(), val plain: String = "", val message: String = "No matching lyrics found", val recordId: Long = 0)

/** Public LRCLIB v1 search; never forwards Google credentials or guesses lyric text. */
object AutomaticLyrics {
    private val cache = android.util.LruCache<String, LyricsResult>(30)
    private fun normal(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    suspend fun find(title: String, creator: String, duration: Long): LyricsResult = withContext(Dispatchers.IO) {
        val cleanTitle = title.replace(Regex("(?i)\\s*[\\[(](official[^\\])]*|lyrics?[^\\])]*|audio[^\\])]*|video[^\\])]*|visuali[sz]er[^\\])]*|hd|4k)[\\])]"), "").trim()
        val split = cleanTitle.split(Regex("\\s+[-–—]\\s+"), limit = 2)
        val artist = (if(split.size == 2) split[0] else creator).replace(Regex("(?i)\\s*(- topic|vevo)$"), "").trim()
        val track = if(split.size == 2) split[1].trim() else cleanTitle
        if(track.isBlank() || duration !in 1000..3_600_000) return@withContext LyricsResult()
        val key = "$artist|$track|${duration / 1000}"
        cache.get(key)?.let { return@withContext it }
        val query = "track_name=${URLEncoder.encode(track.take(200), "UTF-8")}&artist_name=${URLEncoder.encode(artist.take(150), "UTF-8")}"
        val connection = URI("https://lrclib.net/api/search?$query").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10000; connection.readTimeout = 10000; connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "VYBE/1.0 (Android; com.allen.vybe; local development prototype)")
        connection.setRequestProperty("Accept", "application/json")
        try {
            if(connection.responseCode != 200) return@withContext LyricsResult(message = if(connection.responseCode == 429) "Lyrics service is busy. Try again later." else "Lyrics service unavailable")
            val bytes = connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while(true) { val n = stream.read(buffer); if(n < 0) break; require(out.size() + n <= 2_000_000); out.write(buffer, 0, n) }; out.toByteArray()
            }
            val records = JSONArray(bytes.toString(Charsets.UTF_8))
            val matching = (0 until records.length()).map { records.getJSONObject(it) }.filter {
                normal(it.optString("trackName")) == normal(track) && normal(it.optString("artistName")) == normal(artist) && kotlin.math.abs(it.optDouble("duration") - duration / 1000.0) <= 3
            }.minByOrNull { kotlin.math.abs(it.optDouble("duration") - duration / 1000.0) }
            val result = if(matching == null) LyricsResult() else LyricsResult(
                lines = LrcLyrics.parse(matching.optString("syncedLyrics").takeUnless { it == "null" }.orEmpty()),
                plain = matching.optString("plainLyrics").takeUnless { it == "null" }.orEmpty(),
                message = if(matching.optBoolean("instrumental")) "Instrumental recording" else "Lyrics from LRCLIB",
                recordId = matching.optLong("id"))
            cache.put(key, result); result
        } finally { connection.disconnect() }
    }
}

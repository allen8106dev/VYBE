package com.allen.vybe.domain

import java.util.Locale

/** Keep discovery new, playable and bounded; repeated uploads of a title count as one song. */
object RecommendationPolicy {
    private val annotations=Regex("\\([^)]*\\)|\\[[^]]*]")
    private val punctuation=Regex("[^\\p{L}\\p{N}]+")
    private val artistSuffix=Regex("(?i)\\s*(- topic|vevo)$")
    private fun title(value: String) = value.lowercase(Locale.ROOT)
        .replace(annotations, "").replace(punctuation, " ").trim()

    fun select(candidates: List<Track>, excluded: List<Track>, limit: Int = 12): List<Track> {
        val ids=excluded.map { it.id }.toSet()
        val titles=excluded.map { title(it.title) }.filter(String::isNotBlank).toSet()
        return QueueManager.playable(candidates).filter { it.id !in ids && title(it.title) !in titles }
            .distinctBy { it.id }.distinctBy { title(it.title) }.take(limit.coerceIn(0,20))
    }

    fun query(seed: Track): String = seed.artist
        .replace(artistSuffix, "").trim().ifBlank { seed.title }.take(160) + " music"
}

package com.allen.vybe.lyrics

data class LyricLine(val at: Long, val text: String)
object LrcLyrics {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("\\[offset:([+-]?\\d{1,7})]", RegexOption.IGNORE_CASE)
    fun parse(text: String): List<LyricLine> {
        require(text.length <= 262144) { "Lyrics file exceeds 256 KB" }
        val adjustment = offset.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return text.lineSequence().flatMap { line ->
            val marks = stamp.findAll(line).toList()
            val words = line.substring(marks.lastOrNull()?.range?.last?.plus(1) ?: line.length).trim()
            if(words.isEmpty()) emptySequence() else marks.asSequence().mapNotNull { mark ->
                val seconds = mark.groupValues[2].toInt()
                if(seconds >= 60) null else LyricLine((mark.groupValues[1].toLong() * 60000 + seconds * 1000 + mark.groupValues[3].padEnd(3, '0').toLong() + adjustment).coerceAtLeast(0), words)
            }
        }.take(10000).sortedBy { it.at }.toList()
    }
}

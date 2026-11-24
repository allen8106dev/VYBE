package com.allen.vybe

import com.allen.vybe.lyrics.LrcLyrics
import org.junit.Assert.*
import org.junit.Test
class LrcLyricsTest {
    @Test fun supportsMultipleStampsFractionPrecisionSortingAndOffset() {
        val lines = LrcLyrics.parse("[offset:-100]\n[01:02.3][00:02.045]Words\n[00:01.12]First\n[00:61.00]Invalid\n[ar:Metadata]")
        assertEquals(listOf(1020L, 1945L, 62200L), lines.map { it.at })
        assertEquals(listOf("First", "Words", "Words"), lines.map { it.text })
    }
    @Test fun clampsNegativeOffsetAndSkipsUntimedOrEmptyLines() {
        assertEquals(0L, LrcLyrics.parse("[offset:-1000]\n[00:00.2]Start").single().at)
        assertTrue(LrcLyrics.parse("Untimed text\n[00:10.0]").isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedInput() { LrcLyrics.parse("a".repeat(262145)) }
}

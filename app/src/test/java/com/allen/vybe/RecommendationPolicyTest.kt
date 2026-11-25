package com.allen.vybe

import com.allen.vybe.domain.*
import org.junit.Assert.*
import org.junit.Test

class RecommendationPolicyTest {
    private fun song(number: Int,title: String="Song $number")=Track("video"+number.toString().padStart(6,'0'),title,"Artist")

    @Test fun suggestionsExcludeRecentSongsAndAlternateUploads() {
        val played=song(1,"Artist - One (Official Video)")
        val alternate=song(2,"Artist - One [Lyrics]")
        val fresh=song(3)
        assertEquals(listOf(fresh),RecommendationPolicy.select(listOf(played,alternate,fresh),listOf(played)))
    }
    @Test fun suggestionsArePlayableUniqueAndBounded() {
        val songs=(0..30).map { song(it) }
        val candidates=listOf(Track("invalid","Bad","Artist"),song(80).copy(available=false))+songs+ songs
        assertEquals(songs.take(12),RecommendationPolicy.select(candidates,emptyList()))
        assertTrue(RecommendationPolicy.select(songs,emptyList(),-1).isEmpty())
        assertEquals(20,RecommendationPolicy.select(songs,emptyList(),100).size)
    }
    @Test fun artistQueriesRemoveChannelSuffixesAndHandleMissingArtist() {
        assertEquals("Artist music",RecommendationPolicy.query(song(1).copy(artist="Artist - Topic")))
        assertEquals("Artist music",RecommendationPolicy.query(song(1).copy(artist="ArtistVEVO")))
        assertEquals("Song 1 music",RecommendationPolicy.query(song(1).copy(artist="")))
    }
}

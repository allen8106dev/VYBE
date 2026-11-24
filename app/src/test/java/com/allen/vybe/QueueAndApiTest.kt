package com.allen.vybe

import com.allen.vybe.domain.*
import com.allen.vybe.catalog.apiErrorMessage
import org.junit.Assert.*
import org.junit.Test

class QueueAndApiTest {
    @Test fun queueAcceptsCanonicalYoutubeIdsOnly() {
        val song=Track("7wtfhZwyrcc","Believer","Imagine Dragons")
        val invalid=Track("video","Invalid","Channel",Source.YOUTUBE,"https://malicious.invalid/audio")
        assertEquals(listOf(song),QueueManager.playable(listOf(song,invalid)))
    }
    @Test fun queueDropsUnavailableAndInvalidIds() {
        assertTrue(QueueManager.playable(listOf(Track("a", "Unavailable", "Artist", available = false, mediaUri = "asset:///a.wav"), Track("b", "No URI", "Artist"))).isEmpty())
    }
    @Test fun queueMovesStayInsideExistingBounds() {
        assertTrue(QueueManager.canMove(0, 2, 3))
        assertFalse(QueueManager.canMove(-1, 0, 3)); assertFalse(QueueManager.canMove(0, 3, 3))
        assertFalse(QueueManager.canMove(0, 0, 1)); assertFalse(QueueManager.canMove(0, 0, 0))
    }
    @Test fun playlistNamesAreTrimmedAndBounded() {
        assertEquals("Quiet morning", validName("  Quiet morning  "))
        listOf(" ", "x".repeat(151)).forEach { value -> assertThrows(IllegalArgumentException::class.java) { validName(value) } }
    }
    @Test fun apiErrorsDistinguishExpiryQuotaAndMissingContent() {
        assertTrue(apiErrorMessage(401, "").contains("expired"))
        assertTrue(apiErrorMessage(403, "quotaExceeded").contains("quota"))
        assertTrue(apiErrorMessage(403, "forbidden").contains("permissions"))
        assertTrue(apiErrorMessage(404, "videoNotFound").contains("no longer available"))
        assertTrue(apiErrorMessage(503, "backendError").contains("Try again later"))
    }
    @Test fun missingChannelDoesNotPretendAPlaylistWasDeleted() {
        val message = apiErrorMessage(404, "channelNotFound")
        assertTrue(message.contains("channel")); assertTrue(message.contains("same Google account"))
        assertFalse(message.contains("playlist or video"))
        assertTrue(apiErrorMessage(401, "channelNotFound").contains("expired"))
        assertTrue(apiErrorMessage(403, "youtubeSignupRequired").contains("channel setup"))
    }
    @Test fun unknown404DoesNotInventAMissingPlaylist() {
        assertTrue(apiErrorMessage(404, "unknown").contains("HTTP 404"))
        assertFalse(apiErrorMessage(404, "unknown").contains("playlist"))
        assertTrue(apiErrorMessage(404, "playlistNotFound").contains("playlist"))
    }
}

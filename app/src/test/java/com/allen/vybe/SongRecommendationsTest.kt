package com.allen.vybe

import com.allen.vybe.catalog.SongRecommendations
import com.allen.vybe.domain.Track
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SongRecommendationsTest {
    private val seed=Track("video000001","Seed","Artist")
    private val fresh=Track("video000002","New song","Artist")

    @Test fun homeAndAutoplayShareOneRequestAndExcludeAlreadyQueuedSongs() = runBlocking {
        var calls=0
        val gate=CompletableDeferred<Unit>()
        val discovery=Track("video000003","Discovery song","New artist")
        val source=SongRecommendations({calls++;gate.await();listOf(seed,fresh,discovery)},{1000L})
        val home=async { source.songs(seed,listOf(seed)) }
        val autoplay=async { source.songs(seed,listOf(seed,fresh)) }
        yield();gate.complete(Unit)
        assertEquals(listOf(fresh,discovery),home.await());assertEquals(listOf(discovery),autoplay.await())
        assertEquals(1,calls)
    }
    @Test fun chartFallbackLoadsOnlyWhenArtistResultsAreAlreadyExcluded() = runBlocking {
        var calls=0;var time=1000L
        val discovery=Track("video000003","Discovery song","New artist")
        val source=SongRecommendations({seedTrack->calls++;if(seedTrack==null)listOf(discovery) else listOf(seed,fresh)},{time},{wait->time+=wait})
        assertEquals(listOf(discovery),source.songs(seed,listOf(seed,fresh)))
        assertEquals(listOf(fresh,discovery),source.songs(seed,listOf(seed)))
        assertEquals(2,calls)
    }
    @Test fun rapidNewArtistRequestsAreThrottledWithoutInvalidatingCachedSongs() = runBlocking {
        var time=1000L;var calls=0
        var waited=0L
        val source=SongRecommendations({calls++;listOf(fresh)},{time},{wait -> waited+=wait;time+=wait})
        source.songs(seed,emptyList())
        source.songs(seed.copy(artist="Other"),emptyList())
        assertEquals(30000L,waited)
        assertEquals(listOf(fresh),source.songs(seed,emptyList()))
        time+=30001;source.songs(seed.copy(artist="Third"),emptyList())
        assertEquals(3,calls)
    }
    @Test fun disconnectDiscardsAnInFlightRecommendationResponse() = runBlocking {
        val gate=CompletableDeferred<Unit>()
        val source=SongRecommendations({gate.await();listOf(fresh)},{1000L})
        val pending=async { source.songs(seed,emptyList()) }
        yield();source.clear();gate.complete(Unit)
        assertTrue(pending.await().isEmpty())
    }
}

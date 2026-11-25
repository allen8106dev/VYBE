package com.allen.vybe.catalog

import com.allen.vybe.domain.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

/** Shared by Home and autoplay. No timers, account prompts, or unbounded fetches. */
class SongRecommendations(
    private val load: suspend (Track?)->List<Track>,
    private val clock: ()->Long = { android.os.SystemClock.elapsedRealtime() },
    private val pause: suspend (Long)->Unit = { delay(it) }
) {
    constructor(youtube: YouTubeRepository):this(youtube::recommendations)
    private val lock=Mutex()
    private val cache=linkedMapOf<String,Pair<Long,List<Track>>>()
    private var lastRequest=0L
    private var generation=0L

    suspend fun songs(seed: Track?,excluded: List<Track>,refresh: Boolean=false): List<Track> = lock.withLock {
        val key=seed?.let(RecommendationPolicy::query).orEmpty()
        val now=clock()
        val cached=cache[key]
        var tracks: List<Track>
        var fetchedArtistResults=false
        val stamp=generation
        suspend fun requestSlot(): Boolean {
            // Keep discovery off the path needed to start or continue a song.
            if(lastRequest!=0L) {
                val remaining=30000-(clock()-lastRequest)
                if(remaining>0)pause(remaining)
            }
            if(stamp!=generation)return false
            lastRequest=clock()
            return true
        }
        if(!refresh && cached!=null && now-cached.first<30*60*1000L) {
            tracks=cached.second
        } else {
            if(!requestSlot())return@withLock emptyList()
            tracks=load(seed)
            if(stamp!=generation)return@withLock emptyList()
            fetchedArtistResults=seed!=null
        }
        // Avoid the broad chart request unless every artist result is already excluded.
        if(seed!=null && RecommendationPolicy.select(tracks,excluded).isEmpty()) {
            if(!fetchedArtistResults&&!requestSlot())return@withLock emptyList()
            tracks=tracks+load(null)
            if(stamp!=generation)return@withLock emptyList()
        }
        cache[key]=clock() to tracks
        while(cache.size>12)cache.remove(cache.keys.first())
        RecommendationPolicy.select(tracks,excluded)
    }

    fun clear() { generation++;cache.clear() }
}

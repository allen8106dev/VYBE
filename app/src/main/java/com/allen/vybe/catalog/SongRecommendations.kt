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
        if(!refresh && cached!=null && now-cached.first<30*60*1000L)
            return@withLock RecommendationPolicy.select(cached.second,excluded)
        // Protect search quota and leave bandwidth to the playing song.
        val stamp=generation
        if(lastRequest!=0L) {
            val remaining=30000-(now-lastRequest)
            if(remaining>0)pause(remaining)
        }
        if(stamp!=generation)return@withLock emptyList()
        lastRequest=clock()
        val tracks=load(seed)
        if(stamp!=generation)return@withLock emptyList()
        cache[key]=now to tracks
        while(cache.size>12)cache.remove(cache.keys.first())
        RecommendationPolicy.select(tracks,excluded)
    }

    fun clear() { generation++;cache.clear() }
}

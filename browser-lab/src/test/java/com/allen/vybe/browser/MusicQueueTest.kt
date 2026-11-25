package com.allen.vybe.browser

import org.junit.Assert.*
import org.junit.Test

class MusicQueueTest {
    private val songs=(0..4).map { QueueSong("video00000$it","Song $it") }
    @Test fun deduplicatesAndPreservesSelectedSong() {
        val q=MusicQueue();q.replace(listOf(songs[0],songs[0],songs[1]),2)
        assertEquals(2,q.songs.size);assertEquals(songs[1],q.current)
    }
    @Test fun editingAnotherEntryNeverChangesCurrentSong() {
        val q=MusicQueue();q.replace(songs,2);q.move(0,4)
        assertEquals(songs[2],q.current);q.remove(0);assertEquals(songs[2],q.current)
    }
    @Test fun removingCurrentAdvancesAndRemovingLastStops() {
        val q=MusicQueue();q.replace(songs.take(2),0)
        assertTrue(q.remove(0));assertEquals(songs[1],q.current)
        assertTrue(q.remove(0));assertNull(q.current);assertFalse(q.canNext)
    }
    @Test fun shuffleRoundTripKeepsCurrentAndQueueMembership() {
        val q=MusicQueue();q.replace(songs,1);q.toggleShuffle()
        assertEquals(songs[1],q.current);assertEquals(songs.toSet(),q.songs.toSet())
        q.toggleShuffle();assertEquals(songs,q.songs);assertEquals(songs[1],q.current)
    }
    @Test fun naturalCompletionHonoursRepeatModes() {
        val q=MusicQueue();q.replace(songs,4);assertFalse(q.next(true))
        q.setRepeat(1);assertTrue(q.next(true));assertEquals(songs[0],q.current)
        q.setRepeat(2);assertTrue(q.next(true));assertEquals(songs[0],q.current)
        assertTrue(q.next(false));assertEquals(songs[1],q.current)
    }
    @Test fun playNextMovesExistingSongWithoutDuplicates() {
        val q=MusicQueue();q.replace(songs,1);q.add(songs[4],true)
        assertEquals(songs[1],q.current);assertEquals(songs[4],q.songs[q.index+1]);assertEquals(5,q.songs.size)
        q.add(songs[1],true);assertEquals(5,q.songs.size)
        q.toggleShuffle();q.add(songs[3],true);q.toggleShuffle()
        assertEquals(songs[3],q.songs[q.index+1])
    }
    @Test fun clearUpcomingPreservesPlayingSongAndPast() {
        val q=MusicQueue();q.replace(songs,2);q.clearUpcoming()
        assertEquals(songs.take(3),q.songs);assertEquals(songs[2],q.current)
    }
    @Test fun invalidEditsAndIdsCannotCorruptQueue() {
        val q=MusicQueue();q.replace(songs,0);q.move(-1,4);q.remove(99);q.add(QueueSong("invalid","Bad"))
        assertEquals(songs,q.songs);assertFalse(q.select(5))
        q.replace((0..105).map {QueueSong("video"+it.toString().padStart(6,'0'),"Song")},0);assertEquals(100,q.songs.size)
    }
    @Test fun suggestionsCannotJumpAheadOfUserSongsOrOverrideRepeat() {
        val q=MusicQueue();q.replace(songs.take(2),0)
        assertFalse(q.continueWith(songs[2]));assertEquals(songs[0],q.current)
        q.select(1);q.setRepeat(1);assertFalse(q.continueWith(songs[2]))
        q.setRepeat(2);assertFalse(q.continueWith(songs[2]))
        q.setRepeat(0);assertTrue(q.continueWith(songs[2]))
        assertEquals(songs.take(3),q.songs);assertEquals(songs[2],q.current)
        assertTrue(q.previous());assertEquals(songs[1],q.current)
    }
    @Test fun longAutoplaySessionRetainsBoundedQueueAndPreviousSong() {
        val all=(0..105).map { QueueSong("video"+it.toString().padStart(6,'0'),"Song $it") }
        val q=MusicQueue();q.replace(all.take(100),99)
        assertTrue(q.continueWith(all[100]));assertEquals(100,q.songs.size)
        assertEquals(all[1],q.songs.first());assertEquals(all[100],q.current)
        assertFalse(q.continueWith(all[100]));assertFalse(q.continueWith(QueueSong("bad","Bad")))
        q.previous();assertEquals(all[99],q.current)
        q.toggleShuffle();q.toggleShuffle();assertEquals(all[99],q.current)
    }
    @Test fun autoplayDoesNotLoseOriginalOrderWhenShuffleIsTurnedOff() {
        val q=MusicQueue();q.replace(songs.take(4),0);q.toggleShuffle();q.select(3)
        assertTrue(q.continueWith(songs[4]));q.toggleShuffle()
        assertEquals(songs,q.songs);assertEquals(songs[4],q.current)
    }
}

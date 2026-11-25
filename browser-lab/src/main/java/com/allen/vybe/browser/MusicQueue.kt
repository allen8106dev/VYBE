package com.allen.vybe.browser

data class QueueSong(val id: String, val title: String, val artist: String = "", val artistImage: String = "")

/** The service owns this queue; edits preserve the current song rather than restarting it. */
class MusicQueue {
    var ids: List<String> = emptyList(); private set
    var names: List<String> = emptyList(); private set
    var artists: List<String> = emptyList(); private set
    var artistImages: List<String> = emptyList(); private set
    var songs: List<QueueSong> = emptyList()
        private set(value) {
            field=value;ids=value.map { it.id };names=value.map { it.title };artists=value.map { it.artist };artistImages=value.map { it.artistImage }
        }
    var index: Int = 0; private set
    var shuffle: Boolean = false; private set
    var repeat: Int = 0; private set // 0 off, 1 all, 2 one
    private var original = emptyList<QueueSong>()
    val current get() = songs.getOrNull(index)
    val canNext get() = songs.isNotEmpty() && (index < songs.lastIndex || repeat == 1)
    fun replace(items: List<QueueSong>, selected: Int) {
        val wanted=items.getOrNull(selected)?.id
        original=items.filter { it.id == "fixture" || it.id.matches(Regex("[A-Za-z0-9_-]{11}")) }.distinctBy { it.id }.take(100)
        songs=original;index=songs.indexOfFirst { it.id==wanted }.coerceAtLeast(0)
        if(shuffle) shuffleUpcoming()
    }
    fun select(at: Int): Boolean { if(at !in songs.indices)return false; index=at;return true }
    fun next(automatic: Boolean = false): Boolean {
        if(automatic && repeat==2)return songs.isNotEmpty()
        if(!canNext)return false
        index=if(index<songs.lastIndex)index+1 else 0;return true
    }
    fun previous(): Boolean { if(songs.isEmpty())return false;index=if(index>0)index-1 else if(repeat==1)songs.lastIndex else 0;return true }
    fun setRepeat(mode: Int) { repeat=mode.coerceIn(0,2) }
    fun toggleShuffle() {
        val id=current?.id;shuffle=!shuffle
        if(shuffle)shuffleUpcoming() else { songs=original;index=songs.indexOfFirst { it.id==id }.coerceAtLeast(0) }
    }
    private fun shuffleUpcoming() { songs=songs.take(index+1)+songs.drop(index+1).shuffled() }
    fun add(song: QueueSong, next: Boolean = false) {
        if(!(song.id=="fixture"||song.id.matches(Regex("[A-Za-z0-9_-]{11}"))))return
        val currentId=current?.id
        if(song.id==currentId)return
        val without=songs.filterNot { it.id==song.id }
        if(without.size>=100)return
        val at=if(next) (without.indexOfFirst { it.id==currentId }+1).coerceAtLeast(0) else without.size
        songs=without.toMutableList().apply { add(at,song) }
        val originalWithout=original.filterNot { it.id==song.id }
        val originalAt=if(next)(originalWithout.indexOfFirst { it.id==currentId }+1).coerceAtLeast(0) else originalWithout.size
        original=originalWithout.toMutableList().apply { add(originalAt,song) }
        index=songs.indexOfFirst { it.id==currentId }.coerceAtLeast(0)
    }
    fun move(from: Int,to: Int) {
        if(from !in songs.indices||to !in songs.indices||from==to)return
        val id=current?.id;songs=songs.toMutableList().apply { add(to,removeAt(from)) }
        index=songs.indexOfFirst { it.id==id };original=songs
    }
    /** Returns true only if the playing song was removed. */
    fun remove(at: Int): Boolean {
        if(at !in songs.indices)return false
        val old=current?.id;val removed=songs[at].id
        songs=songs.filterIndexed { n,_ -> n!=at };original=original.filterNot { it.id==removed }
        index=if(removed==old)at.coerceAtMost(songs.lastIndex).coerceAtLeast(0) else songs.indexOfFirst { it.id==old }.coerceAtLeast(0)
        return old==removed
    }
    fun clearUpcoming() { songs=songs.take(index+1);original=songs }
    /** Only after the chosen queue ends; retain a bounded history for Previous. */
    fun continueWith(song: QueueSong): Boolean {
        if(songs.isEmpty() || index!=songs.lastIndex || repeat!=0 || song.id in ids || !song.id.matches(Regex("[A-Za-z0-9_-]{11}")))return false
        if(songs.size==100){
            val evicted=songs.first().id
            songs=songs.drop(1);original=original.filterNot { it.id==evicted };index--
        }
        songs=songs+song;original=original+song;index=songs.lastIndex
        return true
    }
    fun clear() { songs=emptyList();original=emptyList();index=0 }
}

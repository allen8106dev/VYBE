package com.allen.vybe.browser

data class BrowserPlayerState(
    val videoId: String = "", val title: String = "Connecting…", val artist: String = "",
    val playing: Boolean = false, val position: Long = 0, val duration: Long = 0,
    val index: Int = 0, val count: Int = 0, val status: String = "", val queue: List<String> = emptyList(),
    val artistImage: String = "", val songs: List<QueueSong> = emptyList(),
    val shuffle: Boolean = false, val repeat: Int = 0, val canNext: Boolean = false,
    val sleepUntil: Long = 0, val error: String? = null
)

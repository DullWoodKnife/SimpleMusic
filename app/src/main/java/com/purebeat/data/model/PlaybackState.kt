package com.purebeat.data.model

enum class PlaybackMode {
    SEQUENCE,    // 顺序播放
    SHUFFLE,     // 随机播放
    REPEAT_ONE   // 单曲循环
}

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENCE,
    val currentPlaylist: List<Song> = emptyList(),
    val currentIndex: Int = -1
) {
    val progress: Float
        get() = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
}

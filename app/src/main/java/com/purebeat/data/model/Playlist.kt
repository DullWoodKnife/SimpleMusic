package com.purebeat.data.model

data class Playlist(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val songCount: Int = 0
)

data class PlaylistWithSongs(
    val playlist: Playlist,
    val songs: List<Song>
)

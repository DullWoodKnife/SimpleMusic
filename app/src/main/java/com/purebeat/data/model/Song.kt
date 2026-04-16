package com.purebeat.data.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val dateAdded: Long,
    val size: Long,
    val mimeType: String
) {
    val durationFormatted: String
        get() {
            val minutes = (duration / 1000) / 60
            val seconds = (duration / 1000) % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

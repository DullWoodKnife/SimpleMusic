package com.purebeat.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_songs",
    primaryKey = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("songMediaId")]
)
data class PlaylistSongCrossRef(
    val id: Long = 0,
    val playlistId: Long,
    val songMediaId: Long,  // MediaStore audio ID
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

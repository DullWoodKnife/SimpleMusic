package com.purebeat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaylistEntity::class, PlaylistSongCrossRef::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "purebeat_database"
    }
}

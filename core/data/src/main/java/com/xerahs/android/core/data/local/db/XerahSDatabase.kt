package com.xerahs.android.core.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        AlbumEntity::class,
        TagEntity::class,
        HistoryTagCrossRef::class
    ],
    version = 2,
    exportSchema = false
)
abstract class XerahSDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun albumDao(): AlbumDao
    abstract fun tagDao(): TagDao
}

package com.xerahs.android.core.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class XerahSDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

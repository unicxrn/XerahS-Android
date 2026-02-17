package com.xerahs.android.core.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        AlbumEntity::class,
        TagEntity::class,
        HistoryTagCrossRef::class,
        UploadProfileEntity::class,
        CustomThemeEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class XerahSDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun albumDao(): AlbumDao
    abstract fun tagDao(): TagDao
    abstract fun uploadProfileDao(): UploadProfileDao
    abstract fun customThemeDao(): CustomThemeDao
}

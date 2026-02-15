package com.xerahs.android.di

import android.content.Context
import androidx.room.Room
import com.xerahs.android.core.data.local.db.AlbumDao
import com.xerahs.android.core.data.local.db.HistoryDao
import com.xerahs.android.core.data.local.db.MIGRATION_1_2
import com.xerahs.android.core.data.local.db.TagDao
import com.xerahs.android.core.data.local.db.XerahSDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): XerahSDatabase =
        Room.databaseBuilder(
            context,
            XerahSDatabase::class.java,
            "xerahs_database"
        )
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideHistoryDao(database: XerahSDatabase): HistoryDao =
        database.historyDao()

    @Provides
    fun provideAlbumDao(database: XerahSDatabase): AlbumDao =
        database.albumDao()

    @Provides
    fun provideTagDao(database: XerahSDatabase): TagDao =
        database.tagDao()
}

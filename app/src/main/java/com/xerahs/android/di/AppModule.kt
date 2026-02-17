package com.xerahs.android.di

import com.xerahs.android.core.data.repository.AlbumRepositoryImpl
import com.xerahs.android.core.data.repository.HistoryRepositoryImpl
import com.xerahs.android.core.data.repository.SettingsRepositoryImpl
import com.xerahs.android.core.data.repository.TagRepositoryImpl
import com.xerahs.android.core.data.repository.UploadProfileRepositoryImpl
import com.xerahs.android.core.domain.repository.AlbumRepository
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.TagRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAlbumRepository(impl: AlbumRepositoryImpl): AlbumRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindUploadProfileRepository(impl: UploadProfileRepositoryImpl): UploadProfileRepository
}

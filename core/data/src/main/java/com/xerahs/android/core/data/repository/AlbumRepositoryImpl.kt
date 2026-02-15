package com.xerahs.android.core.data.repository

import com.xerahs.android.core.data.local.db.AlbumDao
import com.xerahs.android.core.data.local.db.AlbumEntity
import com.xerahs.android.core.data.local.db.HistoryDao
import com.xerahs.android.core.domain.model.Album
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
    private val historyDao: HistoryDao
) : AlbumRepository {

    override fun getAllAlbums(): Flow<List<Album>> =
        albumDao.getAllAlbums().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun createAlbum(album: Album) {
        albumDao.insertAlbum(AlbumEntity.fromDomain(album))
    }

    override suspend fun deleteAlbum(id: String) {
        albumDao.deleteAlbum(id)
    }

    override suspend fun renameAlbum(id: String, name: String) {
        albumDao.renameAlbum(id, name)
    }

    override fun getHistoryByAlbum(albumId: String): Flow<List<HistoryItem>> =
        historyDao.getHistoryByAlbum(albumId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun setAlbum(historyId: String, albumId: String?) {
        historyDao.setAlbum(historyId, albumId)
    }
}

package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.Album
import com.xerahs.android.core.domain.model.HistoryItem
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getAllAlbums(): Flow<List<Album>>
    suspend fun createAlbum(album: Album)
    suspend fun deleteAlbum(id: String)
    suspend fun renameAlbum(id: String, name: String)
    fun getHistoryByAlbum(albumId: String): Flow<List<HistoryItem>>
    suspend fun setAlbum(historyId: String, albumId: String?)
}

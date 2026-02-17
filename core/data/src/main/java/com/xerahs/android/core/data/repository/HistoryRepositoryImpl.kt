package com.xerahs.android.core.data.repository

import com.xerahs.android.core.data.local.db.HistoryDao
import com.xerahs.android.core.data.local.db.HistoryEntity
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadStatistics
import com.xerahs.android.core.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao
) : HistoryRepository {

    override fun getAllHistory(): Flow<List<HistoryItem>> =
        historyDao.getAllHistory().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getHistoryByDestination(destination: UploadDestination): Flow<List<HistoryItem>> =
        historyDao.getHistoryByDestination(destination.name).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getHistoryItem(id: String): HistoryItem? =
        historyDao.getHistoryItem(id)?.toDomain()

    override suspend fun insertHistoryItem(item: HistoryItem) {
        historyDao.insertHistoryItem(HistoryEntity.fromDomain(item))
    }

    override suspend fun deleteHistoryItem(id: String) {
        historyDao.deleteHistoryItem(id)
    }

    override suspend fun clearHistory() {
        historyDao.clearHistory()
    }

    override fun searchHistory(query: String): Flow<List<HistoryItem>> =
        historyDao.searchHistory(query).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getHistoryByDateRange(startTime: Long, endTime: Long): Flow<List<HistoryItem>> =
        historyDao.getHistoryByDateRange(startTime, endTime).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getHistoryByHash(hash: String): HistoryItem? =
        historyDao.getHistoryByHash(hash)?.toDomain()

    override suspend fun getUploadStatistics(): UploadStatistics {
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val uploadsPerDay = historyDao.getUploadsPerDay(weekAgo, 14)
        val weekCount = uploadsPerDay.sumOf { it.count }

        return UploadStatistics(
            totalUploads = historyDao.getTotalUploadCount(),
            totalSize = historyDao.getTotalSize() ?: 0L,
            averageSize = historyDao.getAverageFileSize() ?: 0L,
            uploadsThisWeek = weekCount,
            countByDestination = historyDao.getUploadCountByDestination()
                .map { it.destination to it.count },
            sizeByDestination = historyDao.getTotalSizeByDestination()
                .map { it.destination to it.totalSize },
            uploadsPerDay = uploadsPerDay.map { it.day * 86400000L to it.count },
            topAlbums = historyDao.getTopAlbums().map { it.albumName to it.count },
            topTags = historyDao.getTopTags().map { it.tagName to it.count }
        )
    }
}

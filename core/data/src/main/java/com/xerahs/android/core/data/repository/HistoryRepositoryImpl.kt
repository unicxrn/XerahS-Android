package com.xerahs.android.core.data.repository

import com.xerahs.android.core.data.local.db.HistoryDao
import com.xerahs.android.core.data.local.db.HistoryEntity
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination
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
}

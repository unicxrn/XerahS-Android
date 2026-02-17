package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadStatistics
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<HistoryItem>>
    fun getHistoryByDestination(destination: UploadDestination): Flow<List<HistoryItem>>
    suspend fun getHistoryItem(id: String): HistoryItem?
    suspend fun insertHistoryItem(item: HistoryItem)
    suspend fun deleteHistoryItem(id: String)
    suspend fun clearHistory()
    fun searchHistory(query: String): Flow<List<HistoryItem>>
    fun getHistoryByDateRange(startTime: Long, endTime: Long): Flow<List<HistoryItem>>
    suspend fun getHistoryByHash(hash: String): HistoryItem?
    suspend fun getUploadStatistics(): UploadStatistics
}

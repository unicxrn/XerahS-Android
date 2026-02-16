package com.xerahs.android.core.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE uploadDestination = :destination ORDER BY timestamp DESC")
    fun getHistoryByDestination(destination: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getHistoryItem(id: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistoryItem(id: String)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("SELECT * FROM history WHERE fileName LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getHistoryByDateRange(startTime: Long, endTime: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE albumId = :albumId ORDER BY timestamp DESC")
    fun getHistoryByAlbum(albumId: String): Flow<List<HistoryEntity>>

    @Query("UPDATE history SET albumId = :albumId WHERE id = :historyId")
    suspend fun setAlbum(historyId: String, albumId: String?)

    @Query("SELECT h.* FROM history h INNER JOIN history_tag_cross_ref ref ON h.id = ref.historyId WHERE ref.tagId = :tagId ORDER BY h.timestamp DESC")
    fun getHistoryByTag(tagId: String): Flow<List<HistoryEntity>>

    @Query("SELECT h.* FROM history h INNER JOIN history_tag_cross_ref ref ON h.id = ref.historyId WHERE ref.tagId IN (:tagIds) GROUP BY h.id HAVING COUNT(DISTINCT ref.tagId) = :tagCount ORDER BY h.timestamp DESC")
    fun getHistoryByTags(tagIds: List<String>, tagCount: Int): Flow<List<HistoryEntity>>
}

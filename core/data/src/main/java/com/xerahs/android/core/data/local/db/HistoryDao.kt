package com.xerahs.android.core.data.local.db

import androidx.room.ColumnInfo
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

    @Query("SELECT * FROM history WHERE fileHash = :hash LIMIT 1")
    suspend fun getHistoryByHash(hash: String): HistoryEntity?

    // Aggregate stats queries
    @Query("SELECT uploadDestination AS destination, COUNT(*) AS count FROM history GROUP BY uploadDestination")
    suspend fun getUploadCountByDestination(): List<DestinationCount>

    @Query("SELECT uploadDestination AS destination, SUM(fileSize) AS totalSize FROM history GROUP BY uploadDestination")
    suspend fun getTotalSizeByDestination(): List<DestinationSize>

    @Query("SELECT (timestamp / 86400000) AS day, COUNT(*) AS count FROM history WHERE timestamp >= :startTime GROUP BY day ORDER BY day DESC LIMIT :limit")
    suspend fun getUploadsPerDay(startTime: Long, limit: Int = 14): List<DayCount>

    @Query("SELECT (timestamp / 2592000000) AS month, COUNT(*) AS count FROM history GROUP BY month ORDER BY month DESC")
    suspend fun getUploadsPerMonth(): List<MonthCount>

    @Query("SELECT AVG(fileSize) FROM history")
    suspend fun getAverageFileSize(): Long?

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getTotalUploadCount(): Int

    @Query("SELECT SUM(fileSize) FROM history")
    suspend fun getTotalSize(): Long?

    @Query("SELECT a.name AS albumName, COUNT(h.id) AS count FROM history h INNER JOIN albums a ON h.albumId = a.id GROUP BY h.albumId ORDER BY count DESC LIMIT :limit")
    suspend fun getTopAlbums(limit: Int = 5): List<AlbumCount>

    @Query("SELECT t.name AS tagName, COUNT(ref.historyId) AS count FROM history_tag_cross_ref ref INNER JOIN tags t ON ref.tagId = t.id GROUP BY ref.tagId ORDER BY count DESC LIMIT :limit")
    suspend fun getTopTags(limit: Int = 10): List<TagCount>
}

data class DestinationCount(
    @ColumnInfo(name = "destination") val destination: String,
    @ColumnInfo(name = "count") val count: Int
)

data class DestinationSize(
    @ColumnInfo(name = "destination") val destination: String,
    @ColumnInfo(name = "totalSize") val totalSize: Long
)

data class DayCount(
    @ColumnInfo(name = "day") val day: Long,
    @ColumnInfo(name = "count") val count: Int
)

data class MonthCount(
    @ColumnInfo(name = "month") val month: Long,
    @ColumnInfo(name = "count") val count: Int
)

data class AlbumCount(
    @ColumnInfo(name = "albumName") val albumName: String,
    @ColumnInfo(name = "count") val count: Int
)

data class TagCount(
    @ColumnInfo(name = "tagName") val tagName: String,
    @ColumnInfo(name = "count") val count: Int
)

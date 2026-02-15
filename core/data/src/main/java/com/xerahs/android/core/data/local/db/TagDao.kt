package com.xerahs.android.core.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: String)

    @Query("SELECT t.* FROM tags t INNER JOIN history_tag_cross_ref ref ON t.id = ref.tagId WHERE ref.historyId = :historyId")
    suspend fun getTagsForHistory(historyId: String): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToHistory(crossRef: HistoryTagCrossRef)

    @Query("DELETE FROM history_tag_cross_ref WHERE historyId = :historyId AND tagId = :tagId")
    suspend fun removeTagFromHistory(historyId: String, tagId: String)
}

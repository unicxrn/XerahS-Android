package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun getAllTags(): Flow<List<Tag>>
    suspend fun createTag(tag: Tag)
    suspend fun deleteTag(id: String)
    suspend fun getTagsForHistory(historyId: String): List<Tag>
    suspend fun addTagToHistory(historyId: String, tagId: String)
    suspend fun removeTagFromHistory(historyId: String, tagId: String)
    fun getHistoryByTag(tagId: String): Flow<List<HistoryItem>>
    fun getHistoryByTags(tagIds: Set<String>): Flow<List<HistoryItem>>
}

package com.xerahs.android.core.data.repository

import com.xerahs.android.core.data.local.db.HistoryDao
import com.xerahs.android.core.data.local.db.HistoryTagCrossRef
import com.xerahs.android.core.data.local.db.TagDao
import com.xerahs.android.core.data.local.db.TagEntity
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.Tag
import com.xerahs.android.core.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val historyDao: HistoryDao
) : TagRepository {

    override fun getAllTags(): Flow<List<Tag>> =
        tagDao.getAllTags().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun createTag(tag: Tag) {
        tagDao.insertTag(TagEntity.fromDomain(tag))
    }

    override suspend fun deleteTag(id: String) {
        tagDao.deleteTag(id)
    }

    override suspend fun getTagsForHistory(historyId: String): List<Tag> =
        tagDao.getTagsForHistory(historyId).map { it.toDomain() }

    override suspend fun addTagToHistory(historyId: String, tagId: String) {
        tagDao.addTagToHistory(HistoryTagCrossRef(historyId = historyId, tagId = tagId))
    }

    override suspend fun removeTagFromHistory(historyId: String, tagId: String) {
        tagDao.removeTagFromHistory(historyId, tagId)
    }

    override fun getHistoryByTag(tagId: String): Flow<List<HistoryItem>> =
        historyDao.getHistoryByTag(tagId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getHistoryByTags(tagIds: Set<String>): Flow<List<HistoryItem>> =
        historyDao.getHistoryByTags(tagIds.toList(), tagIds.size).map { entities ->
            entities.map { it.toDomain() }
        }
}

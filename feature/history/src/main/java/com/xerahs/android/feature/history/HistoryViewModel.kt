package com.xerahs.android.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.common.generateTimestamp
import com.xerahs.android.core.domain.model.Album
import com.xerahs.android.core.domain.model.DateFilter
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.Tag
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.AlbumRepository
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val filterDestination: UploadDestination? = null,
    val dateFilter: DateFilter = DateFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val totalUploads: Int = 0,
    val totalSize: Long = 0,
    val albums: List<Album> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val filterAlbumId: String? = null,
    val filterTagId: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val albumRepository: AlbumRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastDeletedItem: HistoryItem? = null

    init {
        loadHistory()
        viewModelScope.launch {
            albumRepository.getAllAlbums().collect { albums ->
                _uiState.value = _uiState.value.copy(albums = albums)
            }
        }
        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.value = _uiState.value.copy(tags = tags)
            }
        }
    }

    private fun loadHistory() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val state = _uiState.value
            val flow = when {
                state.searchQuery.isNotEmpty() ->
                    historyRepository.searchHistory(state.searchQuery)
                state.filterAlbumId != null ->
                    albumRepository.getHistoryByAlbum(state.filterAlbumId)
                state.filterTagId != null ->
                    tagRepository.getHistoryByTag(state.filterTagId)
                state.filterDestination != null ->
                    historyRepository.getHistoryByDestination(state.filterDestination)
                else ->
                    historyRepository.getAllHistory()
            }

            flow.collect { items ->
                val filtered = applyDateFilter(items, _uiState.value.dateFilter)
                // Load tags for each item
                val itemsWithTags = filtered.map { item ->
                    val itemTags = tagRepository.getTagsForHistory(item.id)
                    item.copy(tags = itemTags)
                }
                _uiState.value = _uiState.value.copy(
                    items = itemsWithTags,
                    isLoading = false,
                    totalUploads = itemsWithTags.size,
                    totalSize = itemsWithTags.sumOf { it.fileSize }
                )
            }
        }
    }

    private fun applyDateFilter(items: List<HistoryItem>, filter: DateFilter): List<HistoryItem> {
        if (filter == DateFilter.ALL) return items

        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        val startTime = when (filter) {
            DateFilter.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            DateFilter.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            DateFilter.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            DateFilter.ALL -> 0L
        }

        return items.filter { it.timestamp in startTime..now }
    }

    fun setFilter(destination: UploadDestination?) {
        _uiState.value = _uiState.value.copy(
            filterDestination = destination,
            filterAlbumId = null,
            filterTagId = null,
            isLoading = true
        )
        loadHistory()
    }

    fun setAlbumFilter(albumId: String?) {
        _uiState.value = _uiState.value.copy(
            filterAlbumId = albumId,
            filterDestination = null,
            filterTagId = null,
            isLoading = true
        )
        loadHistory()
    }

    fun setTagFilter(tagId: String?) {
        _uiState.value = _uiState.value.copy(
            filterTagId = tagId,
            filterAlbumId = null,
            filterDestination = null,
            isLoading = true
        )
        loadHistory()
    }

    fun setDateFilter(filter: DateFilter) {
        _uiState.value = _uiState.value.copy(dateFilter = filter, isLoading = true)
        loadHistory()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, isLoading = true)
        loadHistory()
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            lastDeletedItem = historyRepository.getHistoryItem(id)
            historyRepository.deleteHistoryItem(id)
        }
    }

    fun undoDelete() {
        val item = lastDeletedItem ?: return
        lastDeletedItem = null
        viewModelScope.launch {
            historyRepository.insertHistoryItem(item)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    // Album management
    fun createAlbum(name: String) {
        viewModelScope.launch {
            albumRepository.createAlbum(
                Album(id = generateId(), name = name, createdAt = generateTimestamp())
            )
        }
    }

    fun deleteAlbum(id: String) {
        viewModelScope.launch {
            albumRepository.deleteAlbum(id)
            if (_uiState.value.filterAlbumId == id) {
                setAlbumFilter(null)
            }
        }
    }

    fun renameAlbum(id: String, name: String) {
        viewModelScope.launch {
            albumRepository.renameAlbum(id, name)
        }
    }

    fun setItemAlbum(historyId: String, albumId: String?) {
        viewModelScope.launch {
            albumRepository.setAlbum(historyId, albumId)
            loadHistory()
        }
    }

    // Tag management
    fun createTag(name: String) {
        viewModelScope.launch {
            tagRepository.createTag(Tag(id = generateId(), name = name))
        }
    }

    fun deleteTag(id: String) {
        viewModelScope.launch {
            tagRepository.deleteTag(id)
            if (_uiState.value.filterTagId == id) {
                setTagFilter(null)
            }
        }
    }

    fun addTagToItem(historyId: String, tagId: String) {
        viewModelScope.launch {
            tagRepository.addTagToHistory(historyId, tagId)
            loadHistory()
        }
    }

    fun removeTagFromItem(historyId: String, tagId: String) {
        viewModelScope.launch {
            tagRepository.removeTagFromHistory(historyId, tagId)
            loadHistory()
        }
    }
}

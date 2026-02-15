package com.xerahs.android.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.DateFilter
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.HistoryRepository
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
    val totalSize: Long = 0
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastDeletedItem: HistoryItem? = null

    init {
        loadHistory()
    }

    private fun loadHistory() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val flow = when {
                _uiState.value.searchQuery.isNotEmpty() ->
                    historyRepository.searchHistory(_uiState.value.searchQuery)
                _uiState.value.filterDestination != null ->
                    historyRepository.getHistoryByDestination(_uiState.value.filterDestination!!)
                else ->
                    historyRepository.getAllHistory()
            }

            flow.collect { items ->
                val filtered = applyDateFilter(items, _uiState.value.dateFilter)
                _uiState.value = _uiState.value.copy(
                    items = filtered,
                    isLoading = false,
                    totalUploads = filtered.size,
                    totalSize = filtered.sumOf { it.fileSize }
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
        _uiState.value = _uiState.value.copy(filterDestination = destination, isLoading = true)
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
            // Save for undo before deleting
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
}

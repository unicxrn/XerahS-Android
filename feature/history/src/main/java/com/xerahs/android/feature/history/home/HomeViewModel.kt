package com.xerahs.android.feature.history.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.OpenInBrowserException
import com.xerahs.android.core.domain.repository.RemoteDeleteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val sections: List<TimelineSection> = emptyList(),
    val itemsById: Map<String, HistoryItem> = emptyMap(),
    val query: String = "",
    val isLoading: Boolean = true,
    val todayCount: Int = 0
)

sealed interface HomeMessage {
    data class Toast(val text: String) : HomeMessage
    data class OpenUrl(val url: String) : HomeMessage
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val remoteDelete: RemoteDeleteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<HomeMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<HomeMessage> = _messages.asSharedFlow()

    /** All loaded items, newest-first irrelevant - grouping re-sorts. Kept for in-memory filtering. */
    private var allItems: List<HistoryItem> = emptyList()

    init {
        viewModelScope.launch {
            historyRepository.getAllHistory().collect { items ->
                allItems = items
                rebuild()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onQueryChange(q: String) {
        _uiState.value = _uiState.value.copy(query = q)
        rebuild()
    }

    private fun rebuild() {
        val query = _uiState.value.query.trim()
        val visible = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { item ->
                item.fileName.contains(query, ignoreCase = true) ||
                    (item.url?.contains(query, ignoreCase = true) == true)
            }
        }
        val sections = TimelineGrouping.group(
            visible.map { StampedId(it.id, it.timestamp) },
            System.currentTimeMillis()
        )
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayCount = allItems.count { it.timestamp >= startOfDay }
        _uiState.value = _uiState.value.copy(
            sections = sections,
            itemsById = visible.associateBy { it.id },
            todayCount = todayCount
        )
    }

    fun canDeleteFromHost(item: HistoryItem): Boolean = remoteDelete.canDelete(item)

    fun deleteFromHost(item: HistoryItem) {
        viewModelScope.launch {
            remoteDelete.delete(item).fold(
                onSuccess = {
                    try {
                        historyRepository.deleteHistoryItem(item.id)
                        _messages.emit(HomeMessage.Toast("Deleted from ${item.uploadDestination.displayName}"))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _messages.emit(HomeMessage.Toast("Deleted from host, but couldn't update history: ${e.message ?: "unknown error"}"))
                    }
                },
                onFailure = { e ->
                    _messages.emit(
                        if (e is OpenInBrowserException) HomeMessage.OpenUrl(e.url)
                        else HomeMessage.Toast("Couldn't delete: ${e.message ?: "unknown error"}")
                    )
                }
            )
        }
    }
}

package com.xerahs.android.feature.history.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val sections: List<TimelineSection> = emptyList(),
    val itemsById: Map<String, HistoryItem> = emptyMap(),
    val query: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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
        _uiState.value = _uiState.value.copy(
            sections = sections,
            itemsById = visible.associateBy { it.id }
        )
    }
}

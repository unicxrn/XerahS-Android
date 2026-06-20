package com.xerahs.android.feature.history.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.UrlShortenerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareUiState(
    val item: HistoryItem? = null,
    val shortUrl: String? = null,
    val isShortening: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository,
    private val urlShortener: UrlShortenerRepository
) : ViewModel() {

    private val historyId: String = savedStateHandle["historyId"] ?: ""

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(item = historyRepository.getHistoryItem(historyId)) }
        }
    }

    fun shorten() {
        val src = uiState.value.item?.url ?: uiState.value.item?.filePath ?: return
        _uiState.update { it.copy(isShortening = true, error = null) }
        viewModelScope.launch {
            urlShortener.shorten(src).fold(
                onSuccess = { short ->
                    _uiState.update { it.copy(shortUrl = short, isShortening = false) }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(error = "Couldn't shorten link", isShortening = false)
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

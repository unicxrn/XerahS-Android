package com.xerahs.android.feature.history.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.OpenInBrowserException
import com.xerahs.android.core.domain.repository.RemoteDeleteRepository
import com.xerahs.android.core.domain.repository.UrlShortenerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareUiState(
    val item: HistoryItem? = null,
    val shortUrl: String? = null,
    val isShortening: Boolean = false,
    val error: String? = null,
    val canDeleteFromHost: Boolean = false,
    val isDeleting: Boolean = false
)

sealed interface ShareEvent {
    data object Deleted : ShareEvent
    data class OpenUrl(val url: String) : ShareEvent
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository,
    private val urlShortener: UrlShortenerRepository,
    private val remoteDelete: RemoteDeleteRepository
) : ViewModel() {

    private val historyId: String = savedStateHandle["historyId"] ?: ""

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ShareEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val item = historyRepository.getHistoryItem(historyId)
            _uiState.update {
                it.copy(
                    item = item,
                    canDeleteFromHost = item?.let(remoteDelete::canDelete) ?: false
                )
            }
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

    fun deleteFromHost() {
        val item = uiState.value.item ?: return
        _uiState.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            remoteDelete.delete(item).fold(
                onSuccess = {
                    try {
                        historyRepository.deleteHistoryItem(item.id)
                        _uiState.update { it.copy(isDeleting = false) }
                        _events.emit(ShareEvent.Deleted)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isDeleting = false,
                                error = "Deleted from host, but couldn't update history: ${e.message ?: "unknown error"}"
                            )
                        }
                    }
                },
                onFailure = { e ->
                    if (e is OpenInBrowserException) {
                        _uiState.update { it.copy(isDeleting = false) }
                        _events.emit(ShareEvent.OpenUrl(e.url))
                    } else {
                        _uiState.update {
                            it.copy(isDeleting = false, error = "Couldn't delete: ${e.message ?: "unknown error"}")
                        }
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

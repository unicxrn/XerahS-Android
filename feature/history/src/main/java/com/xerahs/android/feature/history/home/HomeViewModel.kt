package com.xerahs.android.feature.history.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import com.xerahs.android.core.domain.model.globalConfigFor
import com.xerahs.android.core.domain.model.isSetupComplete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import com.xerahs.android.core.domain.repository.OpenInBrowserException
import com.xerahs.android.core.domain.repository.RemoteDeleteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.update
import com.xerahs.android.core.domain.model.LinkFormat

data class HomeUiState(
    val sections: List<TimelineSection> = emptyList(),
    val itemsById: Map<String, HistoryItem> = emptyMap(),
    val query: String = "",
    val isLoading: Boolean = true,
    val todayCount: Int = 0,
    /** Default destination that still needs configuring, or null when it is ready to use. */
    val setupNeeded: UploadDestination? = null,
    val linkFormat: LinkFormat = LinkFormat.PLAIN
)

sealed interface HomeMessage {
    data class Toast(val text: String) : HomeMessage
    data class OpenUrl(val url: String) : HomeMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val remoteDelete: RemoteDeleteRepository,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: UploadProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _messages = Channel<HomeMessage>(Channel.BUFFERED)
    val messages: Flow<HomeMessage> = _messages.receiveAsFlow()

    /** All loaded items, newest-first irrelevant - grouping re-sorts. Kept for in-memory filtering. */
    private var allItems: List<HistoryItem> = emptyList()

    private val setupRefresh = MutableStateFlow(0)

    /** Re-check the default destination, for example after coming back from its setup screen. */
    fun refreshSetup() { setupRefresh.value += 1 }

    init {
        viewModelScope.launch {
            settingsRepository.getLinkFormat().collect { format -> _uiState.update { it.copy(linkFormat = format) } }
        }
        viewModelScope.launch {
            combine(settingsRepository.getDefaultDestination(), setupRefresh) { dest, _ -> dest }
                .flatMapLatest { dest ->
                    if (dest == UploadDestination.LOCAL) {
                        flowOf<UploadDestination?>(null)
                    } else {
                        profileRepository.getProfilesForDestination(dest).map { profiles ->
                            val global = settingsRepository.globalConfigFor(dest)
                            val configs = profiles.map { profileRepository.getProfileConfig(it.id, dest) }
                            if (isSetupComplete(global, configs)) null else dest
                        }.flowOn(Dispatchers.IO)
                    }
                }
                .collect { missing -> _uiState.value = _uiState.value.copy(setupNeeded = missing) }
        }
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
                        _messages.send(HomeMessage.Toast("Deleted from ${item.uploadDestination.displayName}"))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _messages.send(HomeMessage.Toast("Deleted from host, but couldn't update history: ${e.message ?: "unknown error"}"))
                    }
                },
                onFailure = { e ->
                    _messages.send(
                        if (e is OpenInBrowserException) HomeMessage.OpenUrl(e.url)
                        else HomeMessage.Toast("Couldn't delete: ${e.message ?: "unknown error"}")
                    )
                }
            )
        }
    }
}

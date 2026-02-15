package com.xerahs.android.feature.s3explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.common.Result
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.feature.s3explorer.data.S3ApiClient
import com.xerahs.android.feature.s3explorer.model.S3Folder
import com.xerahs.android.feature.s3explorer.model.S3ListResult
import com.xerahs.android.feature.s3explorer.model.S3Object
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ViewMode { LIST, GRID }

data class S3ExplorerUiState(
    val isLoading: Boolean = true,
    val isConfigured: Boolean = false,
    val error: String? = null,
    val currentPrefix: String = "",
    val pathSegments: List<String> = emptyList(),
    val folders: List<S3Folder> = emptyList(),
    val objects: List<S3Object> = emptyList(),
    val filteredFolders: List<S3Folder> = emptyList(),
    val filteredObjects: List<S3Object> = emptyList(),
    val searchQuery: String = "",
    val viewMode: ViewMode = ViewMode.LIST,
    val totalFiles: Int = 0,
    val totalSize: Long = 0,
    val selectedObjects: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
    val previewObject: S3Object? = null
)

@HiltViewModel
class S3ExplorerViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val s3ApiClient: S3ApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(S3ExplorerUiState())
    val uiState: StateFlow<S3ExplorerUiState> = _uiState.asStateFlow()

    private var s3Config: UploadConfig.S3Config? = null

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val config = settingsRepository.getS3Config()
            s3Config = config
            val configured = config.accessKeyId.isNotEmpty() &&
                    config.secretAccessKey.isNotEmpty() &&
                    config.bucket.isNotEmpty()
            _uiState.update { it.copy(isConfigured = configured, isLoading = false) }
            if (configured) {
                loadObjects("")
            }
        }
    }

    fun refresh() {
        loadObjects(_uiState.value.currentPrefix)
    }

    private fun loadObjects(prefix: String) {
        val config = s3Config ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = s3ApiClient.listObjects(config, prefix = prefix)) {
                is Result.Success -> {
                    val data = result.data
                    val allObjects = if (data.isTruncated) {
                        loadAllPages(config, prefix, data)
                    } else {
                        data
                    }
                    val segments = if (prefix.isEmpty()) {
                        emptyList()
                    } else {
                        prefix.trimEnd('/').split('/')
                    }
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = null,
                            currentPrefix = prefix,
                            pathSegments = segments,
                            folders = allObjects.folders,
                            objects = allObjects.objects,
                            totalFiles = allObjects.objects.size,
                            totalSize = allObjects.objects.sumOf { it.size },
                            selectedObjects = emptySet()
                        )
                    }
                    applyFilter()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message ?: result.exception.message ?: "Unknown error"
                        )
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    private suspend fun loadAllPages(
        config: UploadConfig.S3Config,
        prefix: String,
        firstPage: S3ListResult
    ): S3ListResult {
        val allObjects = firstPage.objects.toMutableList()
        val allFolders = firstPage.folders.toMutableList()
        var token = firstPage.nextContinuationToken

        while (token != null) {
            when (val result = s3ApiClient.listObjects(config, prefix = prefix, continuationToken = token)) {
                is Result.Success -> {
                    allObjects.addAll(result.data.objects)
                    allFolders.addAll(result.data.folders)
                    token = if (result.data.isTruncated) result.data.nextContinuationToken else null
                }
                else -> break
            }
        }

        return S3ListResult(
            objects = allObjects,
            folders = allFolders,
            isTruncated = false,
            nextContinuationToken = null
        )
    }

    fun navigateToFolder(folder: S3Folder) {
        loadObjects(folder.prefix)
    }

    fun navigateToBreadcrumb(index: Int) {
        val segments = _uiState.value.pathSegments
        if (index < 0) {
            loadObjects("")
        } else {
            val prefix = segments.take(index + 1).joinToString("/") + "/"
            loadObjects(prefix)
        }
    }

    fun navigateToRoot() {
        loadObjects("")
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    private fun applyFilter() {
        _uiState.update { state ->
            val query = state.searchQuery.trim().lowercase()
            if (query.isEmpty()) {
                state.copy(
                    filteredFolders = state.folders,
                    filteredObjects = state.objects
                )
            } else {
                state.copy(
                    filteredFolders = state.folders.filter { it.name.lowercase().contains(query) },
                    filteredObjects = state.objects.filter { it.name.lowercase().contains(query) }
                )
            }
        }
    }

    fun toggleViewMode() {
        _uiState.update { state ->
            state.copy(viewMode = if (state.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
        }
    }

    fun toggleSelection(objectKey: String) {
        _uiState.update { state ->
            val selected = state.selectedObjects.toMutableSet()
            if (selected.contains(objectKey)) {
                selected.remove(objectKey)
            } else {
                selected.add(objectKey)
            }
            state.copy(selectedObjects = selected)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedObjects = emptySet()) }
    }

    fun deleteSelected() {
        val config = s3Config ?: return
        val keys = _uiState.value.selectedObjects.toList()
        if (keys.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            var failCount = 0
            for (key in keys) {
                when (s3ApiClient.deleteObject(config, key)) {
                    is Result.Error -> failCount++
                    else -> {}
                }
            }
            _uiState.update { it.copy(isDeleting = false, selectedObjects = emptySet()) }
            if (failCount > 0) {
                _uiState.update { it.copy(error = "Failed to delete $failCount of ${keys.size} objects") }
            }
            refresh()
        }
    }

    fun deleteSingle(objectKey: String) {
        val config = s3Config ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            when (val result = s3ApiClient.deleteObject(config, objectKey)) {
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            error = result.message ?: "Delete failed"
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isDeleting = false, previewObject = null) }
                    refresh()
                }
            }
        }
    }

    fun setPreviewObject(obj: S3Object?) {
        _uiState.update { it.copy(previewObject = obj) }
    }

    fun getSignedUrl(objectKey: String): Pair<String, Map<String, String>> {
        val config = s3Config ?: return Pair("", emptyMap())
        return s3ApiClient.buildSignedUrl(config, objectKey)
    }

    suspend fun downloadObject(objectKey: String): Result<ByteArray> {
        val config = s3Config ?: return Result.Error(IllegalStateException("S3 not configured"))
        return s3ApiClient.downloadObject(config, objectKey)
    }
}

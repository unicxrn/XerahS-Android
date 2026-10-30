package com.xerahs.android.feature.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.common.file.IntakeCopier
import com.xerahs.android.core.common.file.EditorRouting
import com.xerahs.android.core.common.file.InsufficientStorageException
import com.xerahs.android.core.common.file.copyToCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class BrowseUiState(
    /** A single picked image that opens in the editor. */
    val pickedImagePath: String? = null,
    /** A single video or other file that goes straight to Upload. */
    val pickedFilePath: String? = null,
    val pickedImagePaths: List<String>? = null,
    val errorMessage: String? = null,
    val isCopying: Boolean = false
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private val copier = IntakeCopier<Uri> { uri ->
        copyToCache(context.contentResolver, uri, File(context.filesDir, "captures/pick_${UUID.randomUUID()}"))
    }

    /** Copies every picked item into app storage first, then publishes the result for navigation. */
    fun onPicked(uris: List<Uri>) {
        if (_uiState.value.isCopying) return
        _uiState.value = _uiState.value.copy(isCopying = true)
        viewModelScope.launch {
            try {
                copier.intake(
                    uris,
                    onReady = { files, failed ->
                        val paths = files.map { it.absolutePath }
                        val single = paths.singleOrNull()
                        val failMessage = if (failed > 0) {
                            if (failed == 1) "1 file couldn't be read" else "$failed files couldn't be read"
                        } else null
                        val base = _uiState.value.copy(isCopying = false, errorMessage = failMessage)
                        _uiState.value = when {
                            single == null -> base.copy(pickedImagePaths = paths)
                            EditorRouting.opensInEditor(single) -> base.copy(pickedImagePath = single)
                            else -> base.copy(pickedFilePath = single)
                        }
                    },
                    onFailed = { e ->
                        val msg = if (e is InsufficientStorageException) e.message else "Couldn't read that file"
                        _uiState.value = _uiState.value.copy(isCopying = false, errorMessage = msg)
                    }
                )
            } finally {
                if (_uiState.value.isCopying) _uiState.value = _uiState.value.copy(isCopying = false)
            }
        }
    }

    fun onImageHandled() {
        _uiState.value = _uiState.value.copy(pickedImagePath = null, pickedFilePath = null, pickedImagePaths = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

package com.xerahs.android.feature.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.common.file.IntakeCopier
import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.common.file.copyToCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BrowseUiState(
    /** A single picked image that opens in the editor. */
    val pickedImagePath: String? = null,
    /** A single video or other file that goes straight to Upload. */
    val pickedFilePath: String? = null,
    val pickedImagePaths: List<String>? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private val copier = IntakeCopier<Uri> { uri ->
        copyToCache(context.contentResolver, uri, File(context.filesDir, "captures/pick_${System.nanoTime()}"))
    }

    /** Copies every picked item into app storage first, then publishes the result for navigation. */
    fun onPicked(uris: List<Uri>) {
        viewModelScope.launch {
            copier.intake(
                uris,
                onReady = { files ->
                    val paths = files.map { it.absolutePath }
                    val single = paths.singleOrNull()
                    val mime = single?.let { MimeTypes.fromFileName(it) }
                    _uiState.value = when {
                        single == null -> _uiState.value.copy(pickedImagePaths = paths)
                        MimeTypes.isRasterImage(mime) && !MimeTypes.isHeic(mime) ->
                            _uiState.value.copy(pickedImagePath = single)
                        else -> _uiState.value.copy(pickedFilePath = single)
                    }
                },
                onFailed = { _uiState.value = _uiState.value.copy(errorMessage = "Couldn't read that file") }
            )
        }
    }

    fun onImageHandled() {
        _uiState.value = _uiState.value.copy(pickedImagePath = null, pickedFilePath = null, pickedImagePaths = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

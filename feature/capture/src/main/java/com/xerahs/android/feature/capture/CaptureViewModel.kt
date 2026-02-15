package com.xerahs.android.feature.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class BrowseUiState(
    val pickedImagePath: String? = null,
    val pickedImagePaths: List<String>? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = copyUriToInternal(uri)
                _uiState.value = _uiState.value.copy(pickedImagePath = path)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load image: ${e.message}")
            }
        }
    }

    fun onImagesPicked(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val paths = uris.mapNotNull { uri ->
                    try { copyUriToInternal(uri) } catch (_: Exception) { null }
                }
                if (paths.isEmpty()) {
                    _uiState.value = _uiState.value.copy(errorMessage = "Failed to load images")
                    return@launch
                }
                if (paths.size == 1) {
                    _uiState.value = _uiState.value.copy(pickedImagePath = paths.first())
                } else {
                    _uiState.value = _uiState.value.copy(pickedImagePaths = paths)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load images: ${e.message}")
            }
        }
    }

    fun onImageHandled() {
        _uiState.value = _uiState.value.copy(pickedImagePath = null, pickedImagePaths = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun copyUriToInternal(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot read selected image")
        val capturesDir = File(context.filesDir, "captures")
        if (!capturesDir.exists()) capturesDir.mkdirs()
        val file = File(capturesDir, "browse_${System.currentTimeMillis()}_${uri.hashCode()}.png")
        FileOutputStream(file).use { out ->
            inputStream.copyTo(out)
        }
        inputStream.close()
        return file.absolutePath
    }
}

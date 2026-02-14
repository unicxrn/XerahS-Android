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
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot read selected image")
                val capturesDir = File(context.filesDir, "captures")
                if (!capturesDir.exists()) capturesDir.mkdirs()
                val file = File(capturesDir, "browse_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    inputStream.copyTo(out)
                }
                inputStream.close()
                _uiState.value = _uiState.value.copy(pickedImagePath = file.absolutePath)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load image: ${e.message}")
            }
        }
    }

    fun onImageHandled() {
        _uiState.value = _uiState.value.copy(pickedImagePath = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

package com.xerahs.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class SettingsUiState(
    val defaultDestination: UploadDestination = UploadDestination.IMGUR,
    val overlayEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fileNamingPattern: String = "{original}",
    val exportImportMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val exportImportManager: ExportImportManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            launch {
                settingsRepository.getDefaultDestination().collect { dest ->
                    _uiState.value = _uiState.value.copy(defaultDestination = dest)
                }
            }
            launch {
                settingsRepository.getOverlayEnabled().collect { enabled ->
                    _uiState.value = _uiState.value.copy(overlayEnabled = enabled)
                }
            }
            launch {
                settingsRepository.getThemeMode().collect { mode ->
                    _uiState.value = _uiState.value.copy(themeMode = mode)
                }
            }
            launch {
                settingsRepository.getFileNamingPattern().collect { pattern ->
                    _uiState.value = _uiState.value.copy(fileNamingPattern = pattern)
                }
            }
        }
    }

    fun setDefaultDestination(destination: UploadDestination) {
        viewModelScope.launch {
            settingsRepository.setDefaultDestination(destination)
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOverlayEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setFileNamingPattern(pattern: String) {
        viewModelScope.launch {
            settingsRepository.setFileNamingPattern(pattern)
        }
    }

    fun exportSettings(outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                val json = exportImportManager.exportSettings()
                outputStream.use { it.write(json.toByteArray()) }
                _uiState.value = _uiState.value.copy(exportImportMessage = "Settings exported successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportImportMessage = "Export failed: ${e.message}")
            }
        }
    }

    fun importSettings(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                val json = inputStream.use { it.bufferedReader().readText() }
                exportImportManager.importSettings(json)
                _uiState.value = _uiState.value.copy(exportImportMessage = "Settings imported successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportImportMessage = "Import failed: ${e.message}")
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(exportImportMessage = null)
    }
}

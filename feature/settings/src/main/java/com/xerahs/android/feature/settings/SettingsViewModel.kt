package com.xerahs.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class SettingsUiState(
    val defaultDestination: UploadDestination = UploadDestination.IMGUR,
    val overlayEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fileNamingPattern: String = "{original}",
    val dynamicColor: Boolean = true,
    val colorTheme: ColorTheme = ColorTheme.VIOLET,
    val oledBlack: Boolean = false,
    val imageQuality: Int = 85,
    val maxImageDimension: Int = 0,
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
            launch {
                settingsRepository.getDynamicColor().collect { enabled ->
                    _uiState.value = _uiState.value.copy(dynamicColor = enabled)
                }
            }
            launch {
                settingsRepository.getColorTheme().collect { theme ->
                    _uiState.value = _uiState.value.copy(colorTheme = theme)
                }
            }
            launch {
                settingsRepository.getOledBlack().collect { enabled ->
                    _uiState.value = _uiState.value.copy(oledBlack = enabled)
                }
            }
            launch {
                settingsRepository.getImageQuality().collect { quality ->
                    _uiState.value = _uiState.value.copy(imageQuality = quality)
                }
            }
            launch {
                settingsRepository.getMaxImageDimension().collect { maxDim ->
                    _uiState.value = _uiState.value.copy(maxImageDimension = maxDim)
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

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setColorTheme(theme: ColorTheme) {
        viewModelScope.launch {
            settingsRepository.setColorTheme(theme)
        }
    }

    fun setOledBlack(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOledBlack(enabled)
        }
    }

    fun setImageQuality(quality: Int) {
        viewModelScope.launch {
            settingsRepository.setImageQuality(quality)
        }
    }

    fun setMaxImageDimension(maxDim: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxImageDimension(maxDim)
        }
    }

    fun exportSettings(outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val json = exportImportManager.exportSettings()
                    outputStream.use { it.write(json.toByteArray()) }
                }
                _uiState.value = _uiState.value.copy(exportImportMessage = "Settings exported successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportImportMessage = "Export failed: ${e.message}")
            }
        }
    }

    fun importSettings(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val json = inputStream.use { it.bufferedReader().readText() }
                    exportImportManager.importSettings(json)
                }
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

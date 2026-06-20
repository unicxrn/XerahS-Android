package com.xerahs.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val onboardingCompleted: StateFlow<Boolean> = settingsRepository.getOnboardingCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dynamicColor: StateFlow<Boolean> = settingsRepository.getDynamicColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val colorTheme: StateFlow<ColorTheme> = settingsRepository.getColorTheme()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorTheme.VIOLET)

    val oledBlack: StateFlow<Boolean> = settingsRepository.getOledBlack()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val biometricLockMode: StateFlow<String> = settingsRepository.getBiometricLockMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "OFF")

    val autoLockTimeout: StateFlow<Long> = settingsRepository.getAutoLockTimeout()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _customThemeSeedColor = MutableStateFlow<Int?>(null)
    val customThemeSeedColor: StateFlow<Int?> = _customThemeSeedColor.asStateFlow()

    private val _s3Configured = MutableStateFlow(false)
    val s3Configured: StateFlow<Boolean> = _s3Configured.asStateFlow()

    init {
        viewModelScope.launch {
            val config = settingsRepository.getS3Config()
            _s3Configured.value = config.accessKeyId.isNotEmpty() &&
                config.secretAccessKey.isNotEmpty() &&
                config.bucket.isNotEmpty()
        }
        // Derive the active accent seed from both the selected id AND the themes table, so
        // that changing the seed of the reusable accent entity (same id) still re-emits.
        viewModelScope.launch {
            combine(
                settingsRepository.getCustomThemeId(),
                settingsRepository.getAllCustomThemes()
            ) { themeId, themes ->
                if (themeId != null) themes.firstOrNull { it.id == themeId }?.seedColor else null
            }.collect { seed ->
                _customThemeSeedColor.value = seed
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    fun setDefaultDestination(destination: UploadDestination) {
        viewModelScope.launch {
            settingsRepository.setDefaultDestination(destination)
        }
    }
}

package com.xerahs.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val colorTheme: StateFlow<ColorTheme> = settingsRepository.getColorTheme()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorTheme.VIOLET)

    val oledBlack: StateFlow<Boolean> = settingsRepository.getOledBlack()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val biometricLockMode: StateFlow<String> = settingsRepository.getBiometricLockMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "OFF")

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }
}

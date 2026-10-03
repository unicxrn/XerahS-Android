package com.xerahs.android.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ColorPickerToolViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val recent: StateFlow<List<Int>> = settingsRepository.getRecentColors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remember(color: Int) { viewModelScope.launch { settingsRepository.addRecentColor(color) } }
}

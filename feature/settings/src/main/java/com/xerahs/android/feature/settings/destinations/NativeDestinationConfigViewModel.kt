package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NativeDestinationConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val destination: UploadDestination = UploadDestination.valueOf(checkNotNull(savedStateHandle["destination"]))

    suspend fun load(): Map<String, String> = withContext(Dispatchers.IO) {
        NativeDestinationForms.toValues(
            when (destination) {
                UploadDestination.NEXTCLOUD -> settingsRepository.getNextcloudConfig()
                UploadDestination.IMMICH -> settingsRepository.getImmichConfig()
                UploadDestination.GITHUB_GIST -> settingsRepository.getGistConfig()
                else -> error("$destination has no form")
            }
        )
    }

    // Returns null when saved, otherwise a message for the user.
    suspend fun save(values: Map<String, String>): String? {
        val missing = NativeDestinationForms.missingRequired(destination, values)
        if (missing.isNotEmpty()) return "Fill in: ${missing.joinToString()}"
        withContext(Dispatchers.IO) {
            when (val config = NativeDestinationForms.toConfig(destination, values)) {
                is UploadConfig.NextcloudConfig -> settingsRepository.saveNextcloudConfig(config)
                is UploadConfig.ImmichConfig -> settingsRepository.saveImmichConfig(config)
                is UploadConfig.GistConfig -> settingsRepository.saveGistConfig(config)
                else -> Unit
            }
        }
        return null
    }
}

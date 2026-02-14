package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.ViewModel
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ImgurConfigViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    suspend fun loadConfig(): UploadConfig.ImgurConfig =
        settingsRepository.getImgurConfig()

    suspend fun saveConfig(clientId: String, clientSecret: String, useAnonymous: Boolean) {
        val current = settingsRepository.getImgurConfig()
        settingsRepository.saveImgurConfig(
            current.copy(
                clientId = clientId,
                clientSecret = clientSecret,
                useAnonymous = useAnonymous
            )
        )
    }
}

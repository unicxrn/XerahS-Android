package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.ViewModel
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class S3ConfigViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    suspend fun loadConfig(): UploadConfig.S3Config =
        settingsRepository.getS3Config()

    suspend fun saveConfig(
        accessKeyId: String, secretAccessKey: String, region: String,
        bucket: String, endpoint: String?, customUrl: String?, prefix: String,
        acl: String, usePathStyle: Boolean
    ) {
        settingsRepository.saveS3Config(
            UploadConfig.S3Config(
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                region = region,
                bucket = bucket,
                endpoint = endpoint,
                customUrl = customUrl,
                prefix = prefix,
                acl = acl,
                usePathStyle = usePathStyle
            )
        )
    }
}

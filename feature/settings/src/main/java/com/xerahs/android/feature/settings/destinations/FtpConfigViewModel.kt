package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.ViewModel
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FtpConfigViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    suspend fun loadFtpConfig(): UploadConfig.FtpConfig =
        settingsRepository.getFtpConfig()

    suspend fun loadSftpConfig(): UploadConfig.SftpConfig =
        settingsRepository.getSftpConfig()

    suspend fun saveFtpConfig(
        host: String, port: Int, username: String, password: String,
        remotePath: String, useFtps: Boolean, usePassiveMode: Boolean, httpUrl: String
    ) {
        settingsRepository.saveFtpConfig(
            UploadConfig.FtpConfig(
                host = host, port = port, username = username, password = password,
                remotePath = remotePath, useFtps = useFtps, usePassiveMode = usePassiveMode,
                httpUrl = httpUrl
            )
        )
    }

    suspend fun saveSftpConfig(
        host: String, port: Int, username: String, password: String,
        keyPath: String?, remotePath: String, httpUrl: String
    ) {
        settingsRepository.saveSftpConfig(
            UploadConfig.SftpConfig(
                host = host, port = port, username = username, password = password,
                keyPath = keyPath, remotePath = remotePath, httpUrl = httpUrl
            )
        )
    }
}

package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getDefaultDestination(): Flow<UploadDestination>
    suspend fun setDefaultDestination(destination: UploadDestination)

    fun getOverlayEnabled(): Flow<Boolean>
    suspend fun setOverlayEnabled(enabled: Boolean)

    fun getThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    fun getFileNamingPattern(): Flow<String>
    suspend fun setFileNamingPattern(pattern: String)

    fun getOnboardingCompleted(): Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun getImgurConfig(): UploadConfig.ImgurConfig
    suspend fun saveImgurConfig(config: UploadConfig.ImgurConfig)

    suspend fun getS3Config(): UploadConfig.S3Config
    suspend fun saveS3Config(config: UploadConfig.S3Config)

    suspend fun getFtpConfig(): UploadConfig.FtpConfig
    suspend fun saveFtpConfig(config: UploadConfig.FtpConfig)

    suspend fun getSftpConfig(): UploadConfig.SftpConfig
    suspend fun saveSftpConfig(config: UploadConfig.SftpConfig)
}

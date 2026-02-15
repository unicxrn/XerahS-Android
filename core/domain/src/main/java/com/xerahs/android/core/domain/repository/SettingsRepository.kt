package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.ColorTheme
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

    fun getDynamicColor(): Flow<Boolean>
    suspend fun setDynamicColor(enabled: Boolean)

    fun getColorTheme(): Flow<ColorTheme>
    suspend fun setColorTheme(theme: ColorTheme)

    suspend fun getImgurConfig(): UploadConfig.ImgurConfig
    suspend fun saveImgurConfig(config: UploadConfig.ImgurConfig)

    suspend fun getS3Config(): UploadConfig.S3Config
    suspend fun saveS3Config(config: UploadConfig.S3Config)

    suspend fun getFtpConfig(): UploadConfig.FtpConfig
    suspend fun saveFtpConfig(config: UploadConfig.FtpConfig)

    suspend fun getSftpConfig(): UploadConfig.SftpConfig
    suspend fun saveSftpConfig(config: UploadConfig.SftpConfig)

    fun getOledBlack(): Flow<Boolean>
    suspend fun setOledBlack(enabled: Boolean)

    fun getImageQuality(): Flow<Int>
    suspend fun setImageQuality(quality: Int)

    fun getMaxImageDimension(): Flow<Int>
    suspend fun setMaxImageDimension(maxDim: Int)
}

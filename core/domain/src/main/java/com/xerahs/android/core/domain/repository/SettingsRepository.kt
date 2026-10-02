package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.AfterUploadAction
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.CustomTheme
import com.xerahs.android.core.domain.model.ImageFormat
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
    suspend fun getNextcloudConfig(): UploadConfig.NextcloudConfig
    suspend fun saveNextcloudConfig(config: UploadConfig.NextcloudConfig)
    suspend fun getImmichConfig(): UploadConfig.ImmichConfig
    suspend fun saveImmichConfig(config: UploadConfig.ImmichConfig)
    suspend fun getGistConfig(): UploadConfig.GistConfig
    suspend fun saveGistConfig(config: UploadConfig.GistConfig)

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

    fun getBiometricLockMode(): Flow<String>
    suspend fun setBiometricLockMode(mode: String)

    suspend fun getCustomUploaderConfig(): UploadConfig.CustomUploaderConfig
    suspend fun saveCustomUploaderConfig(config: UploadConfig.CustomUploaderConfig)

    fun getUploadFormat(): Flow<ImageFormat>
    suspend fun setUploadFormat(format: ImageFormat)

    fun getStripExif(): Flow<Boolean>
    suspend fun setStripExif(enabled: Boolean)

    fun getAutoLockTimeout(): Flow<Long>
    suspend fun setAutoLockTimeout(timeout: Long)

    fun getCustomThemeId(): Flow<String?>
    suspend fun setCustomThemeId(id: String?)

    fun getAllCustomThemes(): Flow<List<CustomTheme>>
    suspend fun getCustomTheme(id: String): CustomTheme?
    suspend fun saveCustomTheme(theme: CustomTheme)
    suspend fun deleteCustomTheme(id: String)

    /** Custom-uploader profile used for URL shortening; null = built-in is.gd. */
    fun getShortenerProfileId(): Flow<String?>
    suspend fun setShortenerProfileId(id: String?)

    fun getConvertHeicToPng(): Flow<Boolean>
    suspend fun setConvertHeicToPng(enabled: Boolean)

    fun getDefaultAfterUploadActions(): Flow<Set<AfterUploadAction>>
    suspend fun setDefaultAfterUploadActions(actions: Set<AfterUploadAction>)
    fun getProfileAfterUploadActions(profileId: String): Flow<Set<AfterUploadAction>?>
    suspend fun setProfileAfterUploadActions(profileId: String, actions: Set<AfterUploadAction>?)
    // Profile set if it has one, otherwise the default set.
    suspend fun resolveAfterUploadActions(profileId: String?): Set<AfterUploadAction>
}

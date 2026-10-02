package com.xerahs.android.core.data.repository

import com.xerahs.android.core.data.local.datastore.SecureCredentialStore
import com.xerahs.android.core.data.local.datastore.SettingsDataStore
import com.xerahs.android.core.data.local.db.CustomThemeDao
import com.xerahs.android.core.data.local.db.CustomThemeEntity
import com.xerahs.android.core.domain.model.AfterUploadAction
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.CustomTheme
import com.xerahs.android.core.domain.model.ImageFormat
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val secureCredentialStore: SecureCredentialStore,
    private val customThemeDao: CustomThemeDao
) : SettingsRepository {

    override fun getDefaultDestination(): Flow<UploadDestination> =
        settingsDataStore.getDefaultDestination()

    override suspend fun setDefaultDestination(destination: UploadDestination) =
        settingsDataStore.setDefaultDestination(destination)

    override fun getOverlayEnabled(): Flow<Boolean> =
        settingsDataStore.getOverlayEnabled()

    override suspend fun setOverlayEnabled(enabled: Boolean) =
        settingsDataStore.setOverlayEnabled(enabled)

    override fun getThemeMode(): Flow<ThemeMode> =
        settingsDataStore.getThemeMode()

    override suspend fun setThemeMode(mode: ThemeMode) =
        settingsDataStore.setThemeMode(mode)

    override fun getFileNamingPattern(): Flow<String> =
        settingsDataStore.getFileNamingPattern()

    override suspend fun setFileNamingPattern(pattern: String) =
        settingsDataStore.setFileNamingPattern(pattern)

    override fun getOnboardingCompleted(): Flow<Boolean> =
        settingsDataStore.getOnboardingCompleted()

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        settingsDataStore.setOnboardingCompleted(completed)

    override fun getDynamicColor(): Flow<Boolean> =
        settingsDataStore.getDynamicColor()

    override suspend fun setDynamicColor(enabled: Boolean) =
        settingsDataStore.setDynamicColor(enabled)

    override fun getColorTheme(): Flow<ColorTheme> =
        settingsDataStore.getColorTheme()

    override suspend fun setColorTheme(theme: ColorTheme) =
        settingsDataStore.setColorTheme(theme)

    override suspend fun getImgurConfig(): UploadConfig.ImgurConfig =
        secureCredentialStore.getImgurConfig()

    override suspend fun saveImgurConfig(config: UploadConfig.ImgurConfig) =
        secureCredentialStore.saveImgurConfig(config)

    override suspend fun getS3Config(): UploadConfig.S3Config =
        secureCredentialStore.getS3Config()

    override suspend fun saveS3Config(config: UploadConfig.S3Config) =
        secureCredentialStore.saveS3Config(config)

    override suspend fun getNextcloudConfig(): UploadConfig.NextcloudConfig =
        secureCredentialStore.getNextcloudConfig()

    override suspend fun saveNextcloudConfig(config: UploadConfig.NextcloudConfig) =
        secureCredentialStore.saveNextcloudConfig(config)

    override suspend fun getImmichConfig(): UploadConfig.ImmichConfig =
        secureCredentialStore.getImmichConfig()

    override suspend fun saveImmichConfig(config: UploadConfig.ImmichConfig) =
        secureCredentialStore.saveImmichConfig(config)

    override suspend fun getGistConfig(): UploadConfig.GistConfig =
        secureCredentialStore.getGistConfig()

    override suspend fun saveGistConfig(config: UploadConfig.GistConfig) =
        secureCredentialStore.saveGistConfig(config)

    override suspend fun getFtpConfig(): UploadConfig.FtpConfig =
        secureCredentialStore.getFtpConfig()

    override suspend fun saveFtpConfig(config: UploadConfig.FtpConfig) =
        secureCredentialStore.saveFtpConfig(config)

    override suspend fun getSftpConfig(): UploadConfig.SftpConfig =
        secureCredentialStore.getSftpConfig()

    override suspend fun saveSftpConfig(config: UploadConfig.SftpConfig) =
        secureCredentialStore.saveSftpConfig(config)

    override fun getOledBlack(): Flow<Boolean> =
        settingsDataStore.getOledBlack()

    override suspend fun setOledBlack(enabled: Boolean) =
        settingsDataStore.setOledBlack(enabled)

    override fun getImageQuality(): Flow<Int> =
        settingsDataStore.getImageQuality()

    override suspend fun setImageQuality(quality: Int) =
        settingsDataStore.setImageQuality(quality)

    override fun getMaxImageDimension(): Flow<Int> =
        settingsDataStore.getMaxImageDimension()

    override suspend fun setMaxImageDimension(maxDim: Int) =
        settingsDataStore.setMaxImageDimension(maxDim)

    override fun getBiometricLockMode(): Flow<String> =
        settingsDataStore.getBiometricLockMode()

    override suspend fun setBiometricLockMode(mode: String) =
        settingsDataStore.setBiometricLockMode(mode)

    override suspend fun getCustomUploaderConfig(): UploadConfig.CustomUploaderConfig =
        secureCredentialStore.getCustomUploaderConfig()

    override suspend fun saveCustomUploaderConfig(config: UploadConfig.CustomUploaderConfig) =
        secureCredentialStore.saveCustomUploaderConfig(config)

    override fun getUploadFormat(): Flow<ImageFormat> =
        settingsDataStore.getUploadFormat()

    override suspend fun setUploadFormat(format: ImageFormat) =
        settingsDataStore.setUploadFormat(format)

    override fun getStripExif(): Flow<Boolean> =
        settingsDataStore.getStripExif()

    override suspend fun setStripExif(enabled: Boolean) =
        settingsDataStore.setStripExif(enabled)

    override fun getAutoLockTimeout(): Flow<Long> =
        settingsDataStore.getAutoLockTimeout()

    override suspend fun setAutoLockTimeout(timeout: Long) =
        settingsDataStore.setAutoLockTimeout(timeout)

    override fun getCustomThemeId(): Flow<String?> =
        settingsDataStore.getCustomThemeId()

    override suspend fun setCustomThemeId(id: String?) =
        settingsDataStore.setCustomThemeId(id)

    override fun getAllCustomThemes(): Flow<List<CustomTheme>> =
        customThemeDao.getAllThemes().map { entities ->
            entities.map { CustomTheme(id = it.id, name = it.name, seedColor = it.seedColor) }
        }

    override suspend fun getCustomTheme(id: String): CustomTheme? =
        customThemeDao.getTheme(id)?.let {
            CustomTheme(id = it.id, name = it.name, seedColor = it.seedColor)
        }

    override suspend fun saveCustomTheme(theme: CustomTheme) =
        customThemeDao.insertTheme(
            CustomThemeEntity(
                id = theme.id,
                name = theme.name,
                seedColor = theme.seedColor,
                createdAt = System.currentTimeMillis()
            )
        )

    override suspend fun deleteCustomTheme(id: String) =
        customThemeDao.deleteTheme(id)

    override fun getShortenerProfileId(): Flow<String?> =
        settingsDataStore.getShortenerProfileId()

    override suspend fun setShortenerProfileId(id: String?) =
        settingsDataStore.setShortenerProfileId(id)

    override fun getConvertHeicToPng(): Flow<Boolean> = settingsDataStore.getConvertHeicToPng()

    override suspend fun setConvertHeicToPng(enabled: Boolean) = settingsDataStore.setConvertHeicToPng(enabled)

    override fun getDefaultAfterUploadActions(): Flow<Set<AfterUploadAction>> =
        settingsDataStore.getDefaultAfterUploadActions()

    override suspend fun setDefaultAfterUploadActions(actions: Set<AfterUploadAction>) =
        settingsDataStore.setDefaultAfterUploadActions(actions)

    override fun getProfileAfterUploadActions(profileId: String): Flow<Set<AfterUploadAction>?> =
        settingsDataStore.getProfileAfterUploadActions(profileId)

    override suspend fun setProfileAfterUploadActions(profileId: String, actions: Set<AfterUploadAction>?) =
        settingsDataStore.setProfileAfterUploadActions(profileId, actions)

    override suspend fun resolveAfterUploadActions(profileId: String?): Set<AfterUploadAction> =
        profileId?.let { settingsDataStore.getProfileAfterUploadActions(it).first() }
            ?: settingsDataStore.getDefaultAfterUploadActions().first()
}

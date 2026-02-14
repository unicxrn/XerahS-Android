package com.xerahs.android.core.data.repository

import com.xerahs.android.core.data.local.datastore.SecureCredentialStore
import com.xerahs.android.core.data.local.datastore.SettingsDataStore
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val secureCredentialStore: SecureCredentialStore
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

    override suspend fun getImgurConfig(): UploadConfig.ImgurConfig =
        secureCredentialStore.getImgurConfig()

    override suspend fun saveImgurConfig(config: UploadConfig.ImgurConfig) =
        secureCredentialStore.saveImgurConfig(config)

    override suspend fun getS3Config(): UploadConfig.S3Config =
        secureCredentialStore.getS3Config()

    override suspend fun saveS3Config(config: UploadConfig.S3Config) =
        secureCredentialStore.saveS3Config(config)

    override suspend fun getFtpConfig(): UploadConfig.FtpConfig =
        secureCredentialStore.getFtpConfig()

    override suspend fun saveFtpConfig(config: UploadConfig.FtpConfig) =
        secureCredentialStore.saveFtpConfig(config)

    override suspend fun getSftpConfig(): UploadConfig.SftpConfig =
        secureCredentialStore.getSftpConfig()

    override suspend fun saveSftpConfig(config: UploadConfig.SftpConfig) =
        secureCredentialStore.saveSftpConfig(config)
}

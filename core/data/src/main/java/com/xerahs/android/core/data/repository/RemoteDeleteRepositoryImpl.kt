package com.xerahs.android.core.data.repository

import com.xerahs.android.core.data.remote.delete.RemoteDeleter
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.RemoteDeleteRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteDeleteRepositoryImpl @Inject constructor(
    private val deleter: RemoteDeleter,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: UploadProfileRepository,
) : RemoteDeleteRepository {

    override fun canDelete(item: HistoryItem) = deleter.canDelete(item)

    override suspend fun delete(item: HistoryItem): Result<Unit> = try {
        deleter.delete(item, configFor(item))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // The upload's profile if it still exists, otherwise the global config.
    private suspend fun configFor(item: HistoryItem): UploadConfig = withContext(Dispatchers.IO) {
        val dest = item.uploadDestination
        item.profileId?.takeIf { profileRepository.getProfile(it) != null }
            ?.let { profileRepository.getProfileConfig(it, dest) }
            ?: when (dest) {
                UploadDestination.S3 -> settingsRepository.getS3Config()
                UploadDestination.FTP -> settingsRepository.getFtpConfig()
                UploadDestination.SFTP -> settingsRepository.getSftpConfig()
                UploadDestination.NEXTCLOUD -> settingsRepository.getNextcloudConfig()
                UploadDestination.CUSTOM_HTTP -> settingsRepository.getCustomUploaderConfig()
                else -> throw IllegalArgumentException("Deleting from ${dest.displayName} isn't supported")
            }
    }
}

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

    override suspend fun delete(item: HistoryItem): Result<Unit> {
        return try {
            val config = configFor(item)
                ?: return Result.failure(IllegalStateException("The profile used for this upload was deleted"))
            deleter.delete(item, config)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // The upload's profile config if it still exists, the global config if the upload
    // wasn't tied to a profile, or null if the profile it used has since been deleted
    // (its global config may point at a different bucket/host, so we must not fall
    // back to it and risk deleting an unrelated file with the same key).
    private suspend fun configFor(item: HistoryItem): UploadConfig? = withContext(Dispatchers.IO) {
        val dest = item.uploadDestination
        val profileId = item.profileId
        if (profileId != null) {
            profileRepository.getProfile(profileId)?.let { profileRepository.getProfileConfig(profileId, dest) }
        } else {
            when (dest) {
                UploadDestination.S3 -> settingsRepository.getS3Config()
                UploadDestination.FTP -> settingsRepository.getFtpConfig()
                UploadDestination.SFTP -> settingsRepository.getSftpConfig()
                UploadDestination.NEXTCLOUD -> settingsRepository.getNextcloudConfig()
                UploadDestination.CUSTOM_HTTP -> settingsRepository.getCustomUploaderConfig()
                else -> throw IllegalArgumentException("Deleting from ${dest.displayName} isn't supported")
            }
        }
    }
}

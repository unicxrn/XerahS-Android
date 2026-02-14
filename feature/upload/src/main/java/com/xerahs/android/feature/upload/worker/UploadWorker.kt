package com.xerahs.android.feature.upload.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.xerahs.android.core.common.FileNamePattern
import com.xerahs.android.core.common.ThumbnailGenerator
import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.common.generateTimestamp
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.feature.upload.uploader.FtpUploader
import com.xerahs.android.feature.upload.uploader.ImgurUploader
import com.xerahs.android.feature.upload.uploader.S3Uploader
import com.xerahs.android.feature.upload.uploader.SftpUploader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val imgurUploader: ImgurUploader,
    private val s3Uploader: S3Uploader,
    private val ftpUploader: FtpUploader,
    private val sftpUploader: SftpUploader
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val imagePath = inputData.getString(KEY_IMAGE_PATH) ?: return Result.failure()
        val destinationName = inputData.getString(KEY_DESTINATION) ?: return Result.failure()

        val file = File(imagePath)
        if (!file.exists()) return Result.failure()

        val destination = UploadDestination.valueOf(destinationName)

        val pattern = settingsRepository.getFileNamingPattern().first()
        val resolvedName = FileNamePattern.resolve(pattern, file.name)

        val uploadResult = when (destination) {
            UploadDestination.IMGUR -> {
                val config = settingsRepository.getImgurConfig()
                imgurUploader.upload(file, config, resolvedName)
            }
            UploadDestination.S3 -> {
                val config = settingsRepository.getS3Config()
                s3Uploader.upload(file, config, resolvedName)
            }
            UploadDestination.FTP -> {
                val config = settingsRepository.getFtpConfig()
                ftpUploader.upload(file, config, resolvedName)
            }
            UploadDestination.SFTP -> {
                val config = settingsRepository.getSftpConfig()
                sftpUploader.upload(file, config, resolvedName)
            }
            UploadDestination.LOCAL -> {
                com.xerahs.android.core.domain.model.UploadResult(
                    success = true,
                    url = file.absolutePath,
                    destination = UploadDestination.LOCAL
                )
            }
        }

        if (uploadResult.success) {
            val thumbnailPath = ThumbnailGenerator.generate(appContext, file)

            val historyItem = HistoryItem(
                id = generateId(),
                filePath = imagePath,
                thumbnailPath = thumbnailPath,
                url = uploadResult.url,
                deleteUrl = uploadResult.deleteUrl,
                uploadDestination = destination,
                timestamp = generateTimestamp(),
                fileName = resolvedName,
                fileSize = file.length()
            )
            historyRepository.insertHistoryItem(historyItem)

            val outputData = Data.Builder()
                .putString(KEY_RESULT_URL, uploadResult.url)
                .build()
            return Result.success(outputData)
        }

        return if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_IMAGE_PATH = "image_path"
        const val KEY_DESTINATION = "destination"
        const val KEY_RESULT_URL = "result_url"
    }
}

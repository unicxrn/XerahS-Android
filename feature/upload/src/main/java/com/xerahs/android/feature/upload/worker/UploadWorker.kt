package com.xerahs.android.feature.upload.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.xerahs.android.core.common.FileHasher
import com.xerahs.android.core.common.FileNamePattern
import com.xerahs.android.core.common.ThumbnailGenerator
import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.common.generateTimestamp
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.ImageFormat
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.TagRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import com.xerahs.android.feature.upload.uploader.CustomHttpUploader
import com.xerahs.android.feature.upload.uploader.FtpUploader
import com.xerahs.android.feature.upload.uploader.ImgurUploader
import com.xerahs.android.feature.upload.uploader.S3Uploader
import com.xerahs.android.feature.upload.uploader.SftpUploader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val tagRepository: TagRepository,
    private val imgurUploader: ImgurUploader,
    private val s3Uploader: S3Uploader,
    private val ftpUploader: FtpUploader,
    private val sftpUploader: SftpUploader,
    private val customHttpUploader: CustomHttpUploader,
    private val profileRepository: UploadProfileRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val destinationName = inputData.getString(KEY_DESTINATION) ?: return Result.failure()
        val destination = try {
            UploadDestination.valueOf(destinationName)
        } catch (e: IllegalArgumentException) {
            return Result.failure()
        }
        val albumId = inputData.getString(KEY_ALBUM_ID)
        val tagIds = inputData.getString(KEY_TAG_IDS)?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        val profileId = inputData.getString(KEY_PROFILE_ID)

        // Batch or single mode
        val batchPaths = inputData.getString(KEY_IMAGE_PATHS)?.split("|")?.filter { it.isNotBlank() }
        val singlePath = inputData.getString(KEY_IMAGE_PATH)
        val paths = batchPaths ?: listOfNotNull(singlePath)
        if (paths.isEmpty()) return Result.failure()

        // Show foreground notification
        setForeground(createForegroundInfo("Uploading...", 0, paths.size))

        val skipDuplicateCheck = inputData.getBoolean(KEY_SKIP_DUPLICATE_CHECK, false)

        val urls = mutableListOf<String>()
        for ((index, path) in paths.withIndex()) {
            setForeground(createForegroundInfo("Uploading ${index + 1}/${paths.size}...", index, paths.size))

            val originalFile = File(path)
            if (!originalFile.exists()) continue

            // Compute file hash for duplicate detection
            val fileHash = FileHasher.computeSha256(originalFile)

            // Check for duplicate unless skipped
            if (!skipDuplicateCheck) {
                val existing = historyRepository.getHistoryByHash(fileHash)
                if (existing != null) {
                    val outputData = Data.Builder()
                        .putBoolean(KEY_DUPLICATE_FOUND, true)
                        .putString(KEY_DUPLICATE_URL, existing.url)
                        .putString(KEY_DUPLICATE_FILE_NAME, existing.fileName)
                        .putLong(KEY_DUPLICATE_TIMESTAMP, existing.timestamp)
                        .putString(KEY_IMAGE_PATH, path)
                        .putString(KEY_DESTINATION, destinationName)
                        .build()
                    return Result.failure(outputData)
                }
            }

            val file = prepareFile(originalFile)
            val pattern = settingsRepository.getFileNamingPattern().first()
            val resolvedName = FileNamePattern.resolve(pattern, originalFile.name)

            val uploadResult = performUpload(file, destination, resolvedName, profileId)

            // Clean up temp file
            if (file != originalFile) file.delete()

            if (uploadResult.success) {
                val thumbnailPath = ThumbnailGenerator.generate(appContext, originalFile)
                val itemId = generateId()
                val historyItem = HistoryItem(
                    id = itemId,
                    filePath = path,
                    thumbnailPath = thumbnailPath,
                    url = uploadResult.url,
                    deleteUrl = uploadResult.deleteUrl,
                    uploadDestination = destination,
                    timestamp = generateTimestamp(),
                    fileName = resolvedName,
                    fileSize = originalFile.length(),
                    albumId = albumId,
                    fileHash = fileHash
                )
                historyRepository.insertHistoryItem(historyItem)
                for (tagId in tagIds) {
                    tagRepository.addTagToHistory(itemId, tagId)
                }
                uploadResult.url?.let { urls.add(it) }
            } else {
                postFailureNotification(uploadResult.errorMessage ?: "Upload failed")
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }

        val combinedUrl = urls.joinToString("\n")
        postSuccessNotification(combinedUrl, urls.size)

        val outputData = Data.Builder()
            .putString(KEY_RESULT_URL, combinedUrl)
            .build()
        return Result.success(outputData)
    }

    private suspend fun performUpload(
        file: File,
        destination: UploadDestination,
        resolvedName: String,
        profileId: String? = null
    ): UploadResult {
        // Load config from profile if specified, otherwise use global settings
        val profileConfig = profileId?.let {
            profileRepository.getProfileConfig(it, destination)
        }

        return when (destination) {
            UploadDestination.IMGUR -> {
                val config = (profileConfig as? UploadConfig.ImgurConfig)
                    ?: settingsRepository.getImgurConfig()
                imgurUploader.upload(file, config, resolvedName)
            }
            UploadDestination.S3 -> {
                val config = (profileConfig as? UploadConfig.S3Config)
                    ?: settingsRepository.getS3Config()
                s3Uploader.upload(file, config, resolvedName)
            }
            UploadDestination.FTP -> {
                val config = (profileConfig as? UploadConfig.FtpConfig)
                    ?: settingsRepository.getFtpConfig()
                ftpUploader.upload(file, config, resolvedName)
            }
            UploadDestination.SFTP -> {
                val config = (profileConfig as? UploadConfig.SftpConfig)
                    ?: settingsRepository.getSftpConfig()
                sftpUploader.upload(file, config, resolvedName)
            }
            UploadDestination.CUSTOM_HTTP -> {
                val config = (profileConfig as? UploadConfig.CustomHttpConfig)
                    ?: settingsRepository.getCustomHttpConfig()
                customHttpUploader.upload(file, config, resolvedName)
            }
            UploadDestination.LOCAL -> {
                UploadResult(
                    success = true,
                    url = file.absolutePath,
                    destination = UploadDestination.LOCAL
                )
            }
        }
    }

    private suspend fun prepareFile(file: File): File {
        val quality = settingsRepository.getImageQuality().first()
        val maxDimension = settingsRepository.getMaxImageDimension().first()
        val uploadFormat = settingsRepository.getUploadFormat().first()
        val stripExif = settingsRepository.getStripExif().first()

        val needsProcessing = quality < 100 || maxDimension > 0 || uploadFormat != ImageFormat.ORIGINAL
        if (!needsProcessing && !stripExif) return file

        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return file

        val processedBitmap = if (maxDimension > 0 && (bitmap.width > maxDimension || bitmap.height > maxDimension)) {
            val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            if (scaled != bitmap) bitmap.recycle()
            scaled
        } else {
            bitmap
        }

        val compressFormat: Bitmap.CompressFormat
        val extension: String
        when (uploadFormat) {
            ImageFormat.WEBP -> {
                compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                extension = "webp"
            }
            ImageFormat.JPEG -> {
                compressFormat = Bitmap.CompressFormat.JPEG
                extension = "jpg"
            }
            ImageFormat.ORIGINAL -> {
                compressFormat = Bitmap.CompressFormat.JPEG
                extension = "jpg"
            }
        }

        val tempFile = File(appContext.cacheDir, "upload_${System.currentTimeMillis()}.$extension")
        FileOutputStream(tempFile).use { out ->
            processedBitmap.compress(compressFormat, quality, out)
        }
        processedBitmap.recycle()

        if (stripExif) {
            try {
                val exif = ExifInterface(tempFile.absolutePath)
                val tagsToStrip = listOf(
                    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_ARTIST,
                    ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME_DIGITIZED,
                )
                for (tag in tagsToStrip) {
                    exif.setAttribute(tag, null)
                }
                exif.saveAttributes()
            } catch (_: Exception) { }
        }

        return tempFile
    }

    private fun createForegroundInfo(text: String, current: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_UPLOAD)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("XerahS Upload")
            .setContentText(text)
            .setProgress(total, current, current == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    private fun postSuccessNotification(url: String, count: Int) {
        val copyIntent = Intent(appContext, CopyUrlReceiver::class.java).apply {
            putExtra("url", url)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            appContext, 0, copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (count > 1) "$count uploads complete" else "Upload complete"
        val notification = NotificationCompat.Builder(appContext, CHANNEL_UPLOAD_COMPLETE)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title)
            .setContentText(url.lines().firstOrNull() ?: "")
            .setAutoCancel(true)
            .addAction(0, "Copy URL", copyPendingIntent)
            .build()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    private fun postFailureNotification(errorMessage: String) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_UPLOAD_COMPLETE)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle("Upload failed")
            .setContentText(errorMessage)
            .setAutoCancel(true)
            .build()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    companion object {
        const val KEY_IMAGE_PATH = "image_path"
        const val KEY_IMAGE_PATHS = "image_paths"
        const val KEY_DESTINATION = "destination"
        const val KEY_ALBUM_ID = "album_id"
        const val KEY_TAG_IDS = "tag_ids"
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_RESULT_URL = "result_url"
        const val KEY_SKIP_DUPLICATE_CHECK = "skip_duplicate_check"
        const val KEY_DUPLICATE_FOUND = "duplicate_found"
        const val KEY_DUPLICATE_URL = "duplicate_url"
        const val KEY_DUPLICATE_FILE_NAME = "duplicate_file_name"
        const val KEY_DUPLICATE_TIMESTAMP = "duplicate_timestamp"

        private const val CHANNEL_UPLOAD = "upload_channel"
        private const val CHANNEL_UPLOAD_COMPLETE = "upload_complete_channel"
        private const val NOTIFICATION_ID_PROGRESS = 2001
        private const val NOTIFICATION_ID_COMPLETE = 2002
    }
}

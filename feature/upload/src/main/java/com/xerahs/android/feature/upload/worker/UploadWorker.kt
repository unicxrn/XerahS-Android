package com.xerahs.android.feature.upload.worker

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xerahs.android.core.common.FileHasher
import com.xerahs.android.core.common.FileNamePattern
import com.xerahs.android.core.common.HeicConverter
import com.xerahs.android.core.common.ThumbnailGenerator
import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.common.generateTimestamp
import com.xerahs.android.core.common.image.calculateInSampleSize
import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.domain.model.AfterUploadAction
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.ImageFormat
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.TagRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import com.xerahs.android.core.domain.repository.UrlShortenerRepository
import com.xerahs.android.feature.upload.uploader.CustomHttpUploader
import com.xerahs.android.feature.upload.uploader.FtpUploader
import com.xerahs.android.feature.upload.uploader.GistUploader
import com.xerahs.android.feature.upload.uploader.ImgurUploader
import com.xerahs.android.feature.upload.uploader.ImmichUploader
import com.xerahs.android.feature.upload.uploader.NextcloudUploader
import com.xerahs.android.feature.upload.uploader.S3Uploader
import com.xerahs.android.feature.upload.uploader.SftpUploader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    private val nextcloudUploader: NextcloudUploader,
    private val immichUploader: ImmichUploader,
    private val gistUploader: GistUploader,
    private val profileRepository: UploadProfileRepository,
    private val urlShortener: UrlShortenerRepository
) : CoroutineWorker(appContext, workerParams) {

    private val progress by lazy { BatchProgressStore.forCache(appContext.cacheDir) }

    override suspend fun doWork(): Result {
        try {
            return runBatch()
        } catch (e: CancellationException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (stopReason) {
                    // Android stops dataSync services after a daily limit on API 35: tell the user why.
                    WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT -> withContext(NonCancellable) {
                        val remaining = remainingSpec()
                        UploadNotifier.failure(
                            appContext, id, "Upload stopped by Android after running too long",
                            canRetry = remaining != null, retry = remaining
                        )
                        progress.clear(id)
                        // Cancel so WorkManager does not reschedule the stopped batch.
                        WorkManager.getInstance(applicationContext).cancelWorkById(id)
                    }
                    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> progress.clear(id)
                }
            }
            throw e
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("Uploading...", 0, 0)

    /** The input minus the files this work already finished, or null if nothing is left. */
    private fun remainingSpec(): UploadSpec? {
        val spec = UploadSpec.from(inputData) ?: return null
        val done = progress.completed(id)
        return spec.copy(paths = spec.paths.filterNot { it in done }).takeIf { it.paths.isNotEmpty() }
    }

    private suspend fun runBatch(): Result {
        val spec = UploadSpec.from(inputData) ?: return Result.failure()
        val destination = spec.destination
        val destinationName = destination.name
        val albumId = spec.albumId
        val tagIds = spec.tagIds
        val profileId = spec.profileId
        val inputValues = spec.inputValues
        val paths = spec.paths

        // Files a previous attempt of this work already uploaded: skip them, and keep them out of
        // duplicate detection (they are in history now, because we put them there).
        val done = progress.completed(id)

        // Show foreground notification
        setForeground(createForegroundInfo("Uploading...", 0, paths.size))

        val skipDuplicateCheck = spec.skipDuplicateCheck
        val actions = settingsRepository.resolveAfterUploadActions(profileId)

        val urls = progress.urls(id).toMutableList()
        var lastHistoryId: String? = null
        for ((index, path) in paths.withIndex()) {
            if (path in done) continue
            setForeground(createForegroundInfo("Uploading ${index + 1}/${paths.size}...", index, paths.size))

            val originalFile = File(path)
            if (!originalFile.exists()) continue

            // Compute file hash for duplicate detection
            val fileHash = FileHasher.computeSha256(originalFile)

            // Check for duplicate unless skipped
            if (!skipDuplicateCheck) {
                val existing = historyRepository.getHistoryByHash(fileHash)
                if (existing != null) {
                    progress.clear(id)
                    val outputData = Data.Builder()
                        .putBoolean(KEY_DUPLICATE_FOUND, true)
                        .putString(KEY_DUPLICATE_URL, existing.url)
                        .putString(KEY_DUPLICATE_FILE_NAME, existing.fileName)
                        .putLong(KEY_DUPLICATE_TIMESTAMP, existing.timestamp)
                        .putString(KEY_FILE_PATH, path)
                        .putString(KEY_DESTINATION, destinationName)
                        .build()
                    return Result.failure(outputData)
                }
            }

            val originalMime = MimeTypes.fromFileName(originalFile.name)
            val heicPng = if (MimeTypes.isHeic(originalMime) && settingsRepository.getConvertHeicToPng().first()) {
                HeicConverter.toPng(appContext, originalFile)
            } else null
            val source = heicPng ?: originalFile
            val mimeType = if (heicPng != null) "image/png" else originalMime

            val pattern = settingsRepository.getFileNamingPattern().first()
            val resolvedName = FileNamePattern.resolve(pattern, source.name)
            var file = source
            val uploadResult = try {
                val result = try {
                    file = if (isProcessableImage(mimeType)) prepareFile(source) else source
                    val textInput = if (MimeTypes.isText(mimeType) && source.length() <= MAX_TEXT_INPUT_BYTES) {
                        source.readText()
                    } else ""
                    performUpload(file, destination, resolvedName, profileId, inputValues, textInput)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    UploadResult(
                        success = false,
                        errorMessage = if (e is LocationStripException) e.message
                        else "Upload error: ${e.message ?: e.javaClass.simpleName}",
                        destination = destination,
                        error = e
                    )
                }

                if (result.success) {
                    val thumbnailPath = if (MimeTypes.isRasterImage(mimeType)) {
                        ThumbnailGenerator.generate(appContext, source)
                    } else null
                    val itemId = generateId()
                    val finalUrl = maybeShorten(result.url, actions) { urlShortener.shorten(it) }
                    val historyItem = HistoryItem(
                        id = itemId,
                        filePath = path,
                        thumbnailPath = thumbnailPath,
                        url = finalUrl,
                        deleteUrl = result.deleteUrl,
                        uploadDestination = destination,
                        timestamp = generateTimestamp(),
                        fileName = resolvedName,
                        fileSize = originalFile.length(),
                        albumId = albumId,
                        fileHash = fileHash,
                        mimeType = mimeType,
                        remoteKey = result.remoteKey,
                        profileId = profileId
                    )
                    historyRepository.insertHistoryItem(historyItem)
                    progress.markDone(id, path, finalUrl)
                    for (tagId in tagIds) {
                        tagRepository.addTagToHistory(itemId, tagId)
                    }
                    finalUrl?.let { urls.add(it) }
                    lastHistoryId = itemId
                }
                result
            } finally {
                // Clean up temp files (after the thumbnail, which may read the converted HEIC)
                if (file != source) file.delete()
                heicPng?.parentFile?.deleteRecursively()
            }

            if (!uploadResult.success) {
                val message = uploadResult.errorMessage ?: "Upload failed"
                val kind = UploadErrorPolicy.classify(uploadResult.httpCode, uploadResult.error)
                if (kind == FailureKind.RETRY && runAttemptCount < MAX_RETRIES) return Result.retry()

                // Final: hand back what's left so Retry (notification or queue) picks up where this stopped.
                val remaining = remainingSpec()
                progress.clear(id)
                UploadNotifier.failure(appContext, id, message, canRetry = remaining != null, retry = remaining)
                val output = Data.Builder()
                    .putAll(remaining?.let { UploadEnqueuer.inputData(it) } ?: Data.EMPTY)
                    .putString(KEY_DESTINATION, destinationName)
                    .putString(KEY_ERROR_MESSAGE, message)
                remaining?.let { output.putString(KEY_FILE_PATHS, it.paths.joinToString("|")) }
                return Result.failure(output.build())
            }
        }

        progress.clear(id)
        val combinedUrl = urls.joinToString("\n")
        UploadNotifier.success(
            appContext, id,
            historyId = lastHistoryId.takeIf { paths.size == 1 },
            url = combinedUrl,
            count = urls.size,
            actions = actions
        )

        val outputData = Data.Builder()
            .putString(KEY_RESULT_URL, combinedUrl)
            .putString(KEY_ACTIONS, AfterUploadAction.encode(actions))
            .build()
        return Result.success(outputData)
    }

    private suspend fun performUpload(
        file: File,
        destination: UploadDestination,
        resolvedName: String,
        profileId: String? = null,
        inputValues: Map<String, String> = emptyMap(),
        textInput: String = ""
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
                val config = (profileConfig as? UploadConfig.CustomUploaderConfig)
                    ?: settingsRepository.getCustomUploaderConfig()
                customHttpUploader.upload(file, config, resolvedName, input = textInput, inputValues = inputValues)
            }
            UploadDestination.LOCAL -> {
                UploadResult(
                    success = true,
                    url = file.absolutePath,
                    destination = UploadDestination.LOCAL
                )
            }
            UploadDestination.NEXTCLOUD -> nextcloudUploader.upload(
                file,
                (profileConfig as? UploadConfig.NextcloudConfig) ?: settingsRepository.getNextcloudConfig(),
                resolvedName
            )
            UploadDestination.IMMICH -> immichUploader.upload(
                file,
                (profileConfig as? UploadConfig.ImmichConfig) ?: settingsRepository.getImmichConfig(),
                resolvedName
            )
            UploadDestination.GITHUB_GIST -> gistUploader.upload(
                file,
                (profileConfig as? UploadConfig.GistConfig) ?: settingsRepository.getGistConfig(),
                resolvedName
            )
        }
    }

    /** Raster images we can safely re-encode. GIFs would lose animation; HEIC is converted separately. */
    private fun isProcessableImage(mimeType: String): Boolean =
        MimeTypes.isRasterImage(mimeType) && !MimeTypes.isHeic(mimeType) && mimeType != "image/gif"

    private suspend fun prepareFile(file: File): File {
        val quality = settingsRepository.getImageQuality().first()
        val maxDimension = settingsRepository.getMaxImageDimension().first()
        val uploadFormat = settingsRepository.getUploadFormat().first()
        val stripExif = settingsRepository.getStripExif().first()

        val needsProcessing = quality < 100 || maxDimension > 0 || uploadFormat != ImageFormat.ORIGINAL
        if (!needsProcessing && !stripExif) return file

        // Sample on decode so a 100 MP photo never lands in memory at full size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_EDGE)
        }
        // Can't decode it, so can't strip it either: don't send it out with its location.
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: if (stripExif) throw LocationStripException() else return file

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
            } catch (e: Exception) {
                tempFile.delete()
                throw LocationStripException(e)
            }
        }

        return tempFile
    }

    // dataSync is declared for SystemForegroundService in the app manifest; lint can't see it from this module.
    @SuppressLint("SpecifyForegroundServiceType")
    private fun createForegroundInfo(text: String, current: Int, total: Int): ForegroundInfo {
        val notification = UploadNotifier.progress(appContext, id, text, current, total)
        val notificationId = UploadNotifier.progressIdFor(id)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        // String values kept from the image-only era so work queued by older versions still runs.
        const val KEY_FILE_PATH = "image_path"
        const val KEY_FILE_PATHS = "image_paths"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_TEXT_INPUT_BYTES = 1_000_000L
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
        const val KEY_INPUT_VALUES = "input_values"
        const val KEY_ACTIONS = "after_upload_actions"

        /** Retries after the first attempt; [runAttemptCount] starts at 0. */
        private const val MAX_RETRIES = 3
        private const val MAX_DECODE_EDGE = 4096

        private const val RECORD_SEP = '\u001E'
        private const val UNIT_SEP = '\u001F'

        /** WorkManager Data can't hold maps; encode {inputbox} answers with ASCII separators. */
        fun encodeInputValues(values: Map<String, String>): String =
            values.entries.joinToString(RECORD_SEP.toString()) { "${it.key}$UNIT_SEP${it.value}" }

        fun decodeInputValues(encoded: String?): Map<String, String> =
            if (encoded.isNullOrEmpty()) emptyMap()
            else encoded.split(RECORD_SEP).associate { it.substringBefore(UNIT_SEP) to it.substringAfter(UNIT_SEP, "") }
    }
}

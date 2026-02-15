package com.xerahs.android.feature.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.common.FileNamePattern
import com.xerahs.android.core.common.ThumbnailGenerator
import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.common.generateTimestamp
import com.xerahs.android.core.domain.model.Album
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.Tag
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import com.xerahs.android.core.domain.repository.AlbumRepository
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.TagRepository
import com.xerahs.android.feature.upload.uploader.FtpUploader
import com.xerahs.android.feature.upload.uploader.ImgurUploader
import com.xerahs.android.feature.upload.uploader.S3Uploader
import com.xerahs.android.feature.upload.uploader.SftpUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class UploadUiState(
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val selectedDestination: UploadDestination = UploadDestination.IMGUR,
    val result: UploadResult? = null,
    val errorMessage: String? = null,
    val batchProgress: Pair<Int, Int>? = null,
    val autoCopiableUrl: String? = null,
    val batchUrls: List<String> = emptyList(),
    val autoCopyUrl: Boolean = false,
    val albums: List<Album> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val selectedAlbumId: String? = null,
    val selectedTagIds: Set<String> = emptySet()
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val albumRepository: AlbumRepository,
    private val tagRepository: TagRepository,
    private val imgurUploader: ImgurUploader,
    private val s3Uploader: S3Uploader,
    private val ftpUploader: FtpUploader,
    private val sftpUploader: SftpUploader
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultDest = settingsRepository.getDefaultDestination().first()
            _uiState.value = _uiState.value.copy(selectedDestination = defaultDest)
        }
        viewModelScope.launch {
            settingsRepository.getAutoCopyUrl().collect { enabled ->
                _uiState.value = _uiState.value.copy(autoCopyUrl = enabled)
            }
        }
        viewModelScope.launch {
            albumRepository.getAllAlbums().collect { albums ->
                _uiState.value = _uiState.value.copy(albums = albums)
            }
        }
        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.value = _uiState.value.copy(tags = tags)
            }
        }
    }

    fun selectAlbum(albumId: String?) {
        _uiState.value = _uiState.value.copy(selectedAlbumId = albumId)
    }

    fun toggleTag(tagId: String) {
        val current = _uiState.value.selectedTagIds
        _uiState.value = _uiState.value.copy(
            selectedTagIds = if (tagId in current) current - tagId else current + tagId
        )
    }

    fun selectDestination(destination: UploadDestination) {
        _uiState.value = _uiState.value.copy(selectedDestination = destination)
    }

    private suspend fun prepareFile(file: File): File = withContext(Dispatchers.IO) {
        val quality = settingsRepository.getImageQuality().first()
        val maxDimension = settingsRepository.getMaxImageDimension().first()

        if (quality >= 100 && maxDimension <= 0) return@withContext file

        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext file

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

        val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out ->
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        processedBitmap.recycle()
        tempFile
    }

    fun upload(imagePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null, result = null)

            val originalFile = File(imagePath)
            if (!originalFile.exists()) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    errorMessage = "File not found: $imagePath"
                )
                return@launch
            }

            val file = prepareFile(originalFile)
            val pattern = settingsRepository.getFileNamingPattern().first()
            val resolvedName = FileNamePattern.resolve(pattern, originalFile.name)

            val destination = _uiState.value.selectedDestination
            val result = when (destination) {
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
                    UploadResult(
                        success = true,
                        url = file.absolutePath,
                        destination = UploadDestination.LOCAL
                    )
                }
            }

            if (result.success) {
                val thumbnailPath = ThumbnailGenerator.generate(context, originalFile)
                val itemId = generateId()

                val historyItem = HistoryItem(
                    id = itemId,
                    filePath = imagePath,
                    thumbnailPath = thumbnailPath,
                    url = result.url,
                    deleteUrl = result.deleteUrl,
                    uploadDestination = destination,
                    timestamp = generateTimestamp(),
                    fileName = resolvedName,
                    fileSize = originalFile.length(),
                    albumId = _uiState.value.selectedAlbumId
                )
                historyRepository.insertHistoryItem(historyItem)

                // Assign tags
                for (tagId in _uiState.value.selectedTagIds) {
                    tagRepository.addTagToHistory(itemId, tagId)
                }
            }

            // Clean up temp file
            if (file != originalFile) {
                file.delete()
            }

            _uiState.value = _uiState.value.copy(
                isUploading = false,
                result = result,
                errorMessage = if (!result.success) result.errorMessage else null,
                autoCopiableUrl = if (result.success && _uiState.value.autoCopyUrl) result.url else null
            )
        }
    }

    fun uploadBatch(imagePaths: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true, errorMessage = null, result = null,
                batchProgress = Pair(0, imagePaths.size),
                batchUrls = emptyList()
            )

            val collectedUrls = mutableListOf<String>()
            var lastResult: UploadResult? = null
            for ((index, path) in imagePaths.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    batchProgress = Pair(index + 1, imagePaths.size)
                )

                val originalFile = File(path)
                if (!originalFile.exists()) continue

                val file = prepareFile(originalFile)
                val pattern = settingsRepository.getFileNamingPattern().first()
                val resolvedName = FileNamePattern.resolve(pattern, originalFile.name)

                val destination = _uiState.value.selectedDestination
                val result = when (destination) {
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
                        UploadResult(
                            success = true,
                            url = file.absolutePath,
                            destination = UploadDestination.LOCAL
                        )
                    }
                }

                if (result.success) {
                    val thumbnailPath = ThumbnailGenerator.generate(context, originalFile)
                    val itemId = generateId()
                    val historyItem = HistoryItem(
                        id = itemId,
                        filePath = path,
                        thumbnailPath = thumbnailPath,
                        url = result.url,
                        deleteUrl = result.deleteUrl,
                        uploadDestination = destination,
                        timestamp = generateTimestamp(),
                        fileName = resolvedName,
                        fileSize = originalFile.length(),
                        albumId = _uiState.value.selectedAlbumId
                    )
                    historyRepository.insertHistoryItem(historyItem)

                    // Assign tags
                    for (tagId in _uiState.value.selectedTagIds) {
                        tagRepository.addTagToHistory(itemId, tagId)
                    }
                }

                // Clean up temp file
                if (file != originalFile) {
                    file.delete()
                }

                if (result.success && result.url != null) {
                    collectedUrls.add(result.url!!)
                }
                lastResult = result
                if (!result.success) break
            }

            _uiState.value = _uiState.value.copy(
                isUploading = false,
                result = lastResult,
                batchProgress = null,
                errorMessage = if (lastResult?.success == false) lastResult.errorMessage else null,
                batchUrls = collectedUrls
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

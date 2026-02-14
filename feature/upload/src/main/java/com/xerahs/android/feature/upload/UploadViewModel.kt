package com.xerahs.android.feature.upload

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.common.FileNamePattern
import com.xerahs.android.core.common.ThumbnailGenerator
import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.common.generateTimestamp
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.feature.upload.uploader.FtpUploader
import com.xerahs.android.feature.upload.uploader.ImgurUploader
import com.xerahs.android.feature.upload.uploader.S3Uploader
import com.xerahs.android.feature.upload.uploader.SftpUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UploadUiState(
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val selectedDestination: UploadDestination = UploadDestination.IMGUR,
    val result: UploadResult? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
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
    }

    fun selectDestination(destination: UploadDestination) {
        _uiState.value = _uiState.value.copy(selectedDestination = destination)
    }

    fun upload(imagePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null, result = null)

            val file = File(imagePath)
            if (!file.exists()) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    errorMessage = "File not found: $imagePath"
                )
                return@launch
            }

            val pattern = settingsRepository.getFileNamingPattern().first()
            val resolvedName = FileNamePattern.resolve(pattern, file.name)

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
                val thumbnailPath = ThumbnailGenerator.generate(context, file)

                val historyItem = HistoryItem(
                    id = generateId(),
                    filePath = imagePath,
                    thumbnailPath = thumbnailPath,
                    url = result.url,
                    deleteUrl = result.deleteUrl,
                    uploadDestination = destination,
                    timestamp = generateTimestamp(),
                    fileName = resolvedName,
                    fileSize = file.length()
                )
                historyRepository.insertHistoryItem(historyItem)
            }

            _uiState.value = _uiState.value.copy(
                isUploading = false,
                result = result,
                errorMessage = if (!result.success) result.errorMessage else null
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

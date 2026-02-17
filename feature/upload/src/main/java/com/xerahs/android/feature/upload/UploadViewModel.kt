package com.xerahs.android.feature.upload

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xerahs.android.feature.upload.worker.UploadWorker
import com.xerahs.android.core.domain.model.Album
import com.xerahs.android.core.domain.model.Tag
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.domain.model.UploadResult
import com.xerahs.android.core.domain.repository.AlbumRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.TagRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DuplicateInfo(
    val url: String?,
    val fileName: String?,
    val timestamp: Long,
    val imagePath: String,
    val destination: String
)

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
    val selectedTagIds: Set<String> = emptySet(),
    val duplicateInfo: DuplicateInfo? = null,
    val profiles: List<UploadProfile> = emptyList(),
    val selectedProfileId: String? = null
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val albumRepository: AlbumRepository,
    private val tagRepository: TagRepository,
    private val profileRepository: UploadProfileRepository
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
        viewModelScope.launch {
            profileRepository.getAllProfiles().collect { profiles ->
                _uiState.value = _uiState.value.copy(profiles = profiles)
            }
        }
    }

    fun selectProfile(profileId: String?) {
        _uiState.value = _uiState.value.copy(selectedProfileId = profileId)
        if (profileId != null) {
            val profile = _uiState.value.profiles.find { it.id == profileId }
            if (profile != null) {
                _uiState.value = _uiState.value.copy(selectedDestination = profile.destination)
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

    fun upload(imagePath: String) {
        enqueueUpload(listOf(imagePath))
    }

    fun uploadBatch(imagePaths: List<String>) {
        enqueueUpload(imagePaths)
    }

    private fun enqueueUpload(imagePaths: List<String>, skipDuplicateCheck: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true, errorMessage = null, result = null,
                batchProgress = if (imagePaths.size > 1) Pair(0, imagePaths.size) else null,
                batchUrls = emptyList()
            )

            val destination = _uiState.value.selectedDestination
            val tagIdsString = _uiState.value.selectedTagIds.joinToString("|")

            val inputDataBuilder = Data.Builder()
                .putString(UploadWorker.KEY_DESTINATION, destination.name)
                .putBoolean(UploadWorker.KEY_SKIP_DUPLICATE_CHECK, skipDuplicateCheck)

            _uiState.value.selectedProfileId?.let { profileId ->
                inputDataBuilder.putString(UploadWorker.KEY_PROFILE_ID, profileId)
            }

            if (_uiState.value.selectedAlbumId != null) {
                inputDataBuilder.putString(UploadWorker.KEY_ALBUM_ID, _uiState.value.selectedAlbumId)
            }
            if (tagIdsString.isNotEmpty()) {
                inputDataBuilder.putString(UploadWorker.KEY_TAG_IDS, tagIdsString)
            }

            if (imagePaths.size == 1) {
                inputDataBuilder.putString(UploadWorker.KEY_IMAGE_PATH, imagePaths.first())
            } else {
                inputDataBuilder.putString(UploadWorker.KEY_IMAGE_PATHS, imagePaths.joinToString("|"))
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (destination == UploadDestination.LOCAL) NetworkType.NOT_REQUIRED
                    else NetworkType.CONNECTED
                )
                .build()

            val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(inputDataBuilder.build())
                .setConstraints(constraints)
                .build()

            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(workRequest)

            // Observe work status
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val resultUrl = workInfo.outputData.getString(UploadWorker.KEY_RESULT_URL)
                        val urls = resultUrl?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            result = UploadResult(
                                success = true,
                                url = urls.firstOrNull(),
                                destination = destination
                            ),
                            batchProgress = null,
                            batchUrls = urls,
                            autoCopiableUrl = if (_uiState.value.autoCopyUrl && urls.size == 1) urls.first() else null
                        )
                    }
                    WorkInfo.State.FAILED -> {
                        val isDuplicate = workInfo.outputData.getBoolean(UploadWorker.KEY_DUPLICATE_FOUND, false)
                        if (isDuplicate) {
                            _uiState.value = _uiState.value.copy(
                                isUploading = false,
                                batchProgress = null,
                                duplicateInfo = DuplicateInfo(
                                    url = workInfo.outputData.getString(UploadWorker.KEY_DUPLICATE_URL),
                                    fileName = workInfo.outputData.getString(UploadWorker.KEY_DUPLICATE_FILE_NAME),
                                    timestamp = workInfo.outputData.getLong(UploadWorker.KEY_DUPLICATE_TIMESTAMP, 0L),
                                    imagePath = workInfo.outputData.getString(UploadWorker.KEY_IMAGE_PATH) ?: "",
                                    destination = workInfo.outputData.getString(UploadWorker.KEY_DESTINATION) ?: ""
                                )
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isUploading = false,
                                result = UploadResult(
                                    success = false,
                                    errorMessage = "Upload failed",
                                    destination = destination
                                ),
                                batchProgress = null,
                                errorMessage = "Upload failed"
                            )
                        }
                    }
                    WorkInfo.State.RUNNING -> {
                        // Keep showing uploading state
                    }
                    else -> { }
                }
            }
        }
    }

    fun uploadAnyway() {
        val dupInfo = _uiState.value.duplicateInfo ?: return
        _uiState.value = _uiState.value.copy(duplicateInfo = null)
        enqueueUpload(listOf(dupInfo.imagePath), skipDuplicateCheck = true)
    }

    fun dismissDuplicate() {
        _uiState.value = _uiState.value.copy(duplicateInfo = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

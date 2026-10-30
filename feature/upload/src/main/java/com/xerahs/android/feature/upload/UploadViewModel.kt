package com.xerahs.android.feature.upload

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xerahs.android.feature.upload.worker.UploadEnqueuer
import com.xerahs.android.feature.upload.worker.UploadSpec
import com.xerahs.android.feature.upload.worker.UploadWorker
import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.common.sxcu.CustomDestinationType
import com.xerahs.android.core.common.sxcu.InputPrompt
import com.xerahs.android.core.common.sxcu.ShareXSyntax
import com.xerahs.android.core.domain.model.AfterUploadAction
import com.xerahs.android.core.domain.model.Album
import com.xerahs.android.core.domain.model.DestinationCapabilities
import com.xerahs.android.core.domain.LinkFormatter
import com.xerahs.android.core.domain.model.FileKind
import com.xerahs.android.core.domain.model.LinkFormat
import com.xerahs.android.core.domain.model.fileKindOf
import com.xerahs.android.core.domain.model.Tag
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.domain.model.UploadResult
import com.xerahs.android.core.domain.repository.AlbumRepository
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.TagRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DuplicateInfo(
    val url: String?,
    val fileName: String?,
    val timestamp: Long,
    val imagePath: String,
    val destination: String
)

data class AfterUploadEvent(
    val urls: List<String>,
    val actions: Set<AfterUploadAction>,
    /** The links in the "Copy link as" format, built when the upload finished. */
    val copyText: String,
)

data class UploadUiState(
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val selectedDestination: UploadDestination = UploadDestination.IMGUR,
    val result: UploadResult? = null,
    val errorMessage: String? = null,
    val batchProgress: Pair<Int, Int>? = null,
    val pendingAfterUpload: AfterUploadEvent? = null,
    val batchUrls: List<String> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val selectedAlbumId: String? = null,
    val selectedTagIds: Set<String> = emptySet(),
    val duplicateInfo: DuplicateInfo? = null,
    val profiles: List<UploadProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val pendingPrompts: List<InputPrompt> = emptyList(),
    val fileMimeTypes: List<String> = emptyList(),
    val globalCustomTypes: Set<CustomDestinationType>? = null,
    val profileCustomTypes: Map<String, Set<CustomDestinationType>> = emptyMap(),
    val linkFormat: LinkFormat = LinkFormat.PLAIN,
    // Name and type of a single finished upload, for formatting its link.
    val resultFileName: String? = null,
    val resultIsImage: Boolean? = null,
) {
    /** [links] (one per line) in the "Copy link as" format. */
    fun formatLinks(links: String, fileName: String? = resultFileName, isImage: Boolean? = resultIsImage): String =
        LinkFormatter.formatLinks(links, linkFormat, fileName, isImage)

    fun allows(destination: UploadDestination): Boolean = fileMimeTypes.isEmpty() ||
        DestinationCapabilities.acceptsAll(
            destination, fileMimeTypes,
            if (destination == UploadDestination.CUSTOM_HTTP) globalCustomTypes else null
        )

    fun allows(profile: UploadProfile): Boolean = fileMimeTypes.isEmpty() ||
        DestinationCapabilities.acceptsAll(profile.destination, fileMimeTypes, profileCustomTypes[profile.id])

    /** Profiles of [destination] that can take the current files. */
    fun allowedProfiles(destination: UploadDestination): List<UploadProfile> =
        profiles.filter { it.destination == destination && allows(it) }

    /** A destination is offered when its global config or any of its profiles can take the files. */
    fun isSelectable(destination: UploadDestination): Boolean =
        allows(destination) || allowedProfiles(destination).isNotEmpty()

    val selectableDestinations: List<UploadDestination>
        get() = UploadDestination.entries.filter { isSelectable(it) }

    /**
     * What picking [destination] should select: its global config when allowed, otherwise its
     * first allowed profile. Returns (destination, profileId).
     */
    fun resolveDestination(destination: UploadDestination): Pair<UploadDestination, String?> =
        if (allows(destination)) destination to null
        else allowedProfiles(destination).firstOrNull()?.let { destination to it.id } ?: (destination to null)

    /** Whether the current selection can take the files. */
    val isSelectionAllowed: Boolean
        get() {
            val profile = profiles.find { it.id == selectedProfileId }
            return if (profile != null) allows(profile) else allows(selectedDestination)
        }

    /**
     * Replacement for a disallowed selection: an allowed profile (or the global config) of the same
     * destination first, then the first other destination that can take the files, else LOCAL.
     */
    fun fallbackSelection(): Pair<UploadDestination, String?> {
        allowedProfiles(selectedDestination).firstOrNull()?.let { return selectedDestination to it.id }
        if (allows(selectedDestination)) return selectedDestination to null
        val dest = UploadDestination.entries.firstOrNull { isSelectable(it) } ?: UploadDestination.LOCAL
        return resolveDestination(dest)
    }
}

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

    private var pendingPaths: List<String> = emptyList()
    private var lastInputValues: Map<String, String> = emptyMap()

    // Profile per file kind from "Images go to" and friends. Only a preselection: once the user
    // picks a destination or profile themselves, routing leaves it alone.
    private var routes: Map<FileKind, String> = emptyMap()
    private var userPicked = false

    init {
        viewModelScope.launch {
            val defaultDest = settingsRepository.getDefaultDestination().first()
            routes = FileKind.entries.mapNotNull { kind ->
                settingsRepository.getRouteProfileId(kind).first()?.let { kind to it }
            }.toMap()
            val globalCustomTypes = withContext(Dispatchers.IO) {
                settingsRepository.getCustomUploaderConfig().spec.destinationTypes
            }
            _uiState.value = _uiState.value.copy(
                selectedDestination = defaultDest,
                globalCustomTypes = globalCustomTypes
            )
            applyRoute()
            ensureAllowedSelection()
        }
        viewModelScope.launch {
            settingsRepository.getLinkFormat().collect { format ->
                _uiState.value = _uiState.value.copy(linkFormat = format)
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
                val customTypes = withContext(Dispatchers.IO) {
                    profiles
                        .filter { it.destination == UploadDestination.CUSTOM_HTTP }
                        .associate { p ->
                            p.id to ((profileRepository.getProfileConfig(p.id, UploadDestination.CUSTOM_HTTP)
                                as? UploadConfig.CustomUploaderConfig)?.spec?.destinationTypes ?: emptySet())
                        }
                }
                _uiState.value = _uiState.value.copy(profiles = profiles, profileCustomTypes = customTypes)
                applyRoute()
                ensureAllowedSelection()
            }
        }
    }

    fun selectProfile(profileId: String?) {
        userPicked = true
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
        userPicked = true
        val (dest, profileId) = _uiState.value.resolveDestination(destination)
        _uiState.value = _uiState.value.copy(selectedDestination = dest, selectedProfileId = profileId)
    }

    fun setFiles(paths: List<String>) {
        val mimes = paths.map { MimeTypes.fromFileName(it) }
        if (mimes == _uiState.value.fileMimeTypes) return
        _uiState.value = _uiState.value.copy(fileMimeTypes = mimes)
        applyRoute()
        ensureAllowedSelection()
    }

    /**
     * Preselects the profile routed for the first file's kind. A missing (deleted) or unsuitable
     * profile leaves the Default selection in place.
     */
    private fun applyRoute() {
        if (userPicked) return
        val s = _uiState.value
        val kind = fileKindOf(s.fileMimeTypes.firstOrNull() ?: return)
        val profile = s.profiles.find { it.id == routes[kind] } ?: return
        if (!s.allows(profile)) return
        _uiState.value = s.copy(selectedDestination = profile.destination, selectedProfileId = profile.id)
    }

    /** If the current destination/profile can't take these files, fall back to one that can. */
    private fun ensureAllowedSelection() {
        val s = _uiState.value
        if (!s.isSelectionAllowed) {
            val (dest, profileId) = s.fallbackSelection()
            _uiState.value = s.copy(selectedDestination = dest, selectedProfileId = profileId)
        }
    }

    fun upload(imagePath: String) = startUpload(listOf(imagePath))

    fun uploadBatch(imagePaths: List<String>) = startUpload(imagePaths)

    /** Asks for {inputbox} values first when the selected custom uploader needs them. */
    private fun startUpload(paths: List<String>) {
        viewModelScope.launch {
            val prompts = customUploaderPrompts()
            if (prompts.isEmpty()) {
                enqueueUpload(paths)
            } else {
                pendingPaths = paths
                _uiState.value = _uiState.value.copy(pendingPrompts = prompts)
            }
        }
    }

    private suspend fun customUploaderPrompts(): List<InputPrompt> {
        if (_uiState.value.selectedDestination != UploadDestination.CUSTOM_HTTP) return emptyList()
        return withContext(Dispatchers.IO) {
            val config = _uiState.value.selectedProfileId
                ?.let { profileRepository.getProfileConfig(it, UploadDestination.CUSTOM_HTTP) }
                as? UploadConfig.CustomUploaderConfig
                ?: settingsRepository.getCustomUploaderConfig()
            ShareXSyntax.inputPrompts(config.spec.requestTemplates())
        }
    }

    fun submitPromptValues(values: Map<String, String>) {
        lastInputValues = values
        _uiState.value = _uiState.value.copy(pendingPrompts = emptyList())
        enqueueUpload(pendingPaths, inputValues = values)
    }

    fun cancelPrompts() {
        _uiState.value = _uiState.value.copy(pendingPrompts = emptyList())
    }

    private fun enqueueUpload(
        imagePaths: List<String>,
        skipDuplicateCheck: Boolean = false,
        inputValues: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true, errorMessage = null, result = null,
                batchProgress = if (imagePaths.size > 1) Pair(0, imagePaths.size) else null,
                batchUrls = emptyList()
            )

            val destination = _uiState.value.selectedDestination
            val workId = UploadEnqueuer.enqueue(
                context,
                UploadSpec(
                    paths = imagePaths,
                    destination = destination,
                    profileId = _uiState.value.selectedProfileId,
                    albumId = _uiState.value.selectedAlbumId,
                    tagIds = _uiState.value.selectedTagIds.toList(),
                    inputValues = inputValues,
                    skipDuplicateCheck = skipDuplicateCheck,
                )
            )
            val workManager = WorkManager.getInstance(context)

            // Observe work status
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val resultUrl = workInfo.outputData.getString(UploadWorker.KEY_RESULT_URL)
                        val urls = resultUrl?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                        val fileName = workInfo.outputData.getString(UploadWorker.KEY_RESULT_FILE_NAME)
                        val isImage = workInfo.outputData.keyValueMap[UploadWorker.KEY_RESULT_IS_IMAGE] as? Boolean
                        _uiState.value = _uiState.value.copy(
                            resultFileName = fileName,
                            resultIsImage = isImage,
                            isUploading = false,
                            result = UploadResult(
                                success = true,
                                url = urls.firstOrNull(),
                                destination = destination
                            ),
                            batchProgress = null,
                            batchUrls = urls,
                            pendingAfterUpload = AfterUploadEvent(
                                urls,
                                AfterUploadAction.decode(workInfo.outputData.getString(UploadWorker.KEY_ACTIONS)),
                                copyText = _uiState.value.formatLinks(urls.joinToString("\n"), fileName, isImage),
                            ).takeIf { it.urls.isNotEmpty() && it.actions.isNotEmpty() }
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
                                    imagePath = workInfo.outputData.getString(UploadWorker.KEY_FILE_PATH) ?: "",
                                    destination = workInfo.outputData.getString(UploadWorker.KEY_DESTINATION) ?: ""
                                )
                            )
                        } else {
                            val message = workInfo.outputData.getString(UploadWorker.KEY_ERROR_MESSAGE) ?: "Upload failed"
                            _uiState.value = _uiState.value.copy(
                                isUploading = false,
                                result = UploadResult(
                                    success = false,
                                    errorMessage = message,
                                    destination = destination
                                ),
                                batchProgress = null,
                                errorMessage = message
                            )
                        }
                    }
                    WorkInfo.State.RUNNING -> {
                        val total = workInfo.progress.getInt(UploadWorker.KEY_PROGRESS_TOTAL, 0)
                        if (total > 1) {
                            val done = workInfo.progress.getInt(UploadWorker.KEY_PROGRESS_DONE, 0)
                            _uiState.value = _uiState.value.copy(batchProgress = Pair(done, total))
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        _uiState.value = _uiState.value.copy(isUploading = false, batchProgress = null)
                    }
                    else -> { }
                }
            }
        }
    }

    fun uploadAnyway() {
        val dupInfo = _uiState.value.duplicateInfo ?: return
        _uiState.value = _uiState.value.copy(duplicateInfo = null)
        enqueueUpload(listOf(dupInfo.imagePath), skipDuplicateCheck = true, inputValues = lastInputValues)
    }

    fun dismissDuplicate() {
        _uiState.value = _uiState.value.copy(duplicateInfo = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun consumeAfterUpload() {
        _uiState.value = _uiState.value.copy(pendingAfterUpload = null)
    }
}

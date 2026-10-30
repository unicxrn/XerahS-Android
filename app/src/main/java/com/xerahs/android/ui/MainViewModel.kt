package com.xerahs.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.common.file.InsufficientStorageException
import com.xerahs.android.core.common.file.IntakeCopier
import com.xerahs.android.core.common.file.copyToCache
import com.xerahs.android.core.common.sxcu.ShareXSyntax
import com.xerahs.android.core.domain.model.BiometricLockMode
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import com.xerahs.android.feature.upload.worker.UploadEnqueuer
import com.xerahs.android.feature.upload.worker.UploadSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: UploadProfileRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // App lock state lives here so a rotation does not ask for the fingerprint again.
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()
    /** When the app went to the background while unlocked; 0 means it was locked (or never left). */
    var lastBackgroundTime: Long = 0L

    private val _lockNeedsSetup = MutableStateFlow(false)
    val lockNeedsSetup: StateFlow<Boolean> = _lockNeedsSetup.asStateFlow()
    fun setLockNeedsSetup(value: Boolean) { _lockNeedsSetup.value = value }

    fun setUnlocked(value: Boolean) { _isUnlocked.value = value }

    // Pending share intake, backed by SavedStateHandle so it survives rotation and process death.
    val pendingSharedPaths: StateFlow<List<String>?> =
        savedStateHandle.getStateFlow<ArrayList<String>?>(KEY_PATHS, null)
    /** Profile the pending share must upload to (a direct share that still needs input), else null. */
    val pendingSharedProfileId: StateFlow<String?> = savedStateHandle.getStateFlow<String?>(KEY_SHARE_PROFILE, null)
    /** Path of a copied uploader config (.sxcu) waiting for the import screen. */
    val pendingImportPath: StateFlow<String?> = savedStateHandle.getStateFlow<String?>(KEY_IMPORT, null)
    val pendingLaunchCapture: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_CAPTURE, false)
    val pendingDeepLink: StateFlow<String?> = savedStateHandle.getStateFlow<String?>(KEY_DEEP_LINK, null)

    fun clearSharedPaths() {
        savedStateHandle[KEY_SHARE_PROFILE] = null
        savedStateHandle[KEY_PATHS] = null
    }
    /**
     * Routes files that are already in app storage (a downloaded link, a text file) like a share.
     * With [profileId] they go straight to the Upload screen with that profile picked.
     */
    fun offerSharedPaths(paths: List<String>, profileId: String? = null) {
        // Set the profile first so the paths collector never sees new paths with a stale profile.
        savedStateHandle[KEY_SHARE_PROFILE] = profileId
        savedStateHandle[KEY_PATHS] = ArrayList(paths)
    }
    fun clearImportPath() { savedStateHandle[KEY_IMPORT] = null }
    fun clearLaunchCapture() { savedStateHandle[KEY_CAPTURE] = false }
    /** An uploader config opened from another app: copied now, while the read grant still holds. */
    fun handleImport(uri: Uri) {
        viewModelScope.launch { importConfig(uri) }
    }
    fun requestLaunchCapture() { savedStateHandle[KEY_CAPTURE] = true }
    fun setDeepLink(uri: String) { savedStateHandle[KEY_DEEP_LINK] = uri }
    fun clearDeepLink() { savedStateHandle[KEY_DEEP_LINK] = null }

    // Setup screen to open once after onboarding; survives rotation like the other pending state.
    val pendingSetupRoute: StateFlow<String?> = savedStateHandle.getStateFlow<String?>(KEY_SETUP_ROUTE, null)
    fun clearSetupRoute() { savedStateHandle[KEY_SETUP_ROUTE] = null }

    /** Copies a shared item into app storage right away, in a scope that outlives rotation. */
    fun handleSend(uri: Uri?, text: String?) {
        viewModelScope.launch {
            if (uri != null) {
                val name = withContext(Dispatchers.IO) { displayName(uri) }
                if (isUploaderConfig(name)) {
                    importConfig(uri)
                } else {
                    intake(listOf(uri))
                }
            } else if (text != null) {
                withContext(Dispatchers.IO) { writeSharedText(text) }
                    ?.let { offerSharedPaths(listOf(it)) }
            }
        }
    }

    fun handleSendMultiple(uris: List<Uri>) {
        viewModelScope.launch {
            intake(uris)
        }
    }

    private val _intakeMessages = Channel<String>(Channel.BUFFERED)
    /** Messages about shares that could not be read; shown as a toast by the activity. */
    val intakeMessages: Flow<String> = _intakeMessages.receiveAsFlow()

    fun showIntakeMessage(text: String) { _intakeMessages.trySend(text) }

    private val _finishRequests = Channel<Unit>(Channel.BUFFERED)
    /** Emits when a direct share is queued and the activity was opened only for it. */
    val finishRequests: Flow<Unit> = _finishRequests.receiveAsFlow()

    /**
     * A share sent through a profile's sharing shortcut: copy right away, then queue the upload to
     * that profile without asking. A deleted profile falls back to the normal share flow.
     */
    fun uploadToProfile(profileId: String, uris: List<Uri>, text: String?, finishAfter: Boolean) {
        viewModelScope.launch {
            val profile = profileRepository.getProfile(profileId)
            val single = uris.singleOrNull()
            val isConfig = single != null && isUploaderConfig(withContext(Dispatchers.IO) { displayName(single) })
            // A deleted profile or an uploader config file takes the normal share flow.
            if (profile == null || isConfig) {
                when {
                    single != null -> handleSend(single, null)
                    uris.isNotEmpty() -> intake(uris)
                    else -> handleSend(null, text)
                }
                return@launch
            }
            val paths: List<String> = if (uris.isNotEmpty()) busy {
                var copied: List<String> = emptyList()
                IntakeCopier<Uri>(::copyShared).intake(
                    uris,
                    onReady = { files, failed ->
                        copied = files.map(File::getAbsolutePath)
                        if (failed > 0) _intakeMessages.trySend(if (failed == 1) "1 file couldn't be read" else "$failed files couldn't be read")
                    },
                    onFailed = { e ->
                        _intakeMessages.trySend(
                            if (e is InsufficientStorageException) e.message ?: "Couldn't read that file" else "Couldn't read that file"
                        )
                    }
                )
                copied
            } else {
                listOfNotNull(text?.let { withContext(Dispatchers.IO) { writeSharedText(it) } })
            }
            if (paths.isEmpty()) return@launch
            // A custom uploader that asks for values needs the Upload screen.
            if (asksForInput(profile)) {
                offerSharedPaths(paths, profile.id)
                return@launch
            }
            UploadEnqueuer.enqueue(
                context,
                UploadSpec(paths = paths, destination = profile.destination, profileId = profile.id, background = true)
            )
            Toast.makeText(context, "Uploading to ${profile.name.ifBlank { profile.destination.displayName }}", Toast.LENGTH_SHORT).show()
            if (finishAfter) _finishRequests.trySend(Unit)
        }
    }

    private suspend fun asksForInput(profile: UploadProfile): Boolean {
        if (profile.destination != UploadDestination.CUSTOM_HTTP) return false
        return withContext(Dispatchers.IO) {
            val config = profileRepository.getProfileConfig(profile.id, UploadDestination.CUSTOM_HTTP)
                as? UploadConfig.CustomUploaderConfig ?: return@withContext false
            ShareXSyntax.inputPrompts(config.spec.requestTemplates()).isNotEmpty()
        }
    }

    private val _intakeBusy = MutableStateFlow(false)
    /** True while a shared or clipboard file is being copied into app storage. */
    val intakeBusy: StateFlow<Boolean> = _intakeBusy.asStateFlow()

    private suspend fun intake(uris: List<Uri>) = busy {
        IntakeCopier<Uri>(::copyShared).intake(
            uris,
            onReady = { files, failed ->
                offerSharedPaths(files.map(File::getAbsolutePath))
                if (failed > 0) _intakeMessages.trySend(if (failed == 1) "1 file couldn't be read" else "$failed files couldn't be read")
            },
            onFailed = { e ->
                _intakeMessages.trySend(
                    if (e is InsufficientStorageException) e.message ?: "Couldn't read that file" else "Couldn't read that file"
                )
            }
        )
    }

    /**
     * Copies an uploader config into the cache now, while the read grant still holds, so the
     * import screen reads a file of ours (also after process death) and never the URI.
     */
    private suspend fun importConfig(uri: Uri) {
        val path = busy {
            withContext(Dispatchers.IO) {
                runCatching {
                    // Keep the name: the importer tells .sxcu from .xsdc by its extension.
                    val name = displayName(uri)?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "uploader.sxcu"
                    val dir = File(context.cacheDir, "imports/${UUID.randomUUID()}").apply { mkdirs() }
                    val file = File(dir, name)
                    val ok = context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { out -> copyCapped(input, out) }
                    } == true
                    if (ok) file.absolutePath else {
                        dir.deleteRecursively()
                        null
                    }
                }.getOrNull()
            }
        }
        if (path == null) {
            _intakeMessages.trySend("Couldn't read that file")
        } else {
            savedStateHandle[KEY_IMPORT] = path
        }
    }

    /** Copies at most [MAX_IMPORT_BYTES]; false when the input is larger (it is no uploader config). */
    private fun copyCapped(input: java.io.InputStream, out: java.io.OutputStream): Boolean {
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return true
            total += n
            if (total > MAX_IMPORT_BYTES) return false
            out.write(buf, 0, n)
        }
    }

    private suspend fun <T> busy(block: suspend () -> T): T {
        _intakeBusy.value = true
        return try {
            block()
        } finally {
            _intakeBusy.value = false
        }
    }

    private suspend fun copyShared(uri: Uri): File {
        val dir = newSharedDir()
        return try {
            copyToCache(context.contentResolver, uri, dir)
        } catch (e: Exception) {
            dir.deleteRecursively() // don't leave empty share folders behind
            throw e
        }
    }

    fun isUploaderConfig(name: String?): Boolean =
        name?.substringAfterLast('.', "")?.lowercase() in setOf("sxcu", "xsdc")

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun newSharedDir(): File =
        File(context.filesDir, "captures/shared_${UUID.randomUUID()}").apply { mkdirs() }

    private fun writeSharedText(text: String): String? = runCatching {
        File(newSharedDir(), "shared-text.txt").apply { writeText(text) }.absolutePath
    }.getOrNull()

    private companion object {
        const val KEY_PATHS = "pendingSharedPaths"
        const val KEY_IMPORT = "pendingImportPath"
        const val KEY_SHARE_PROFILE = "pendingSharedProfileId"
        const val MAX_IMPORT_BYTES = 1024 * 1024
        const val KEY_CAPTURE = "pendingLaunchCapture"
        const val KEY_DEEP_LINK = "pendingDeepLink"
        const val KEY_SETUP_ROUTE = "pendingSetupRoute"
    }


    val themeMode: StateFlow<ThemeMode> = settingsRepository.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    // null until DataStore has loaded, so the UI never shows Home or onboarding on a guess.
    val onboardingCompleted: StateFlow<Boolean?> = settingsRepository.getOnboardingCompleted()
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dynamicColor: StateFlow<Boolean> = settingsRepository.getDynamicColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val colorTheme: StateFlow<ColorTheme> = settingsRepository.getColorTheme()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorTheme.VIOLET)

    val oledBlack: StateFlow<Boolean> = settingsRepository.getOledBlack()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // null until DataStore has loaded, so the UI never renders content on a guessed lock state.
    val biometricLockMode: StateFlow<BiometricLockMode?> = settingsRepository.getBiometricLockMode()
        .map<String, BiometricLockMode?> { BiometricLockMode.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val autoLockTimeout: StateFlow<Long> = settingsRepository.getAutoLockTimeout()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _customThemeSeedColor = MutableStateFlow<Int?>(null)
    val customThemeSeedColor: StateFlow<Int?> = _customThemeSeedColor.asStateFlow()

    private val _s3Configured = MutableStateFlow(false)
    val s3Configured: StateFlow<Boolean> = _s3Configured.asStateFlow()

    init {
        viewModelScope.launch {
            val config = settingsRepository.getS3Config()
            _s3Configured.value = config.accessKeyId.isNotEmpty() &&
                config.secretAccessKey.isNotEmpty() &&
                config.bucket.isNotEmpty()
        }
        // Derive the active accent seed from both the selected id AND the themes table, so
        // that changing the seed of the reusable accent entity (same id) still re-emits.
        viewModelScope.launch {
            combine(
                settingsRepository.getCustomThemeId(),
                settingsRepository.getAllCustomThemes()
            ) { themeId, themes ->
                if (themeId != null) themes.firstOrNull { it.id == themeId }?.seedColor else null
            }.collect { seed ->
                _customThemeSeedColor.value = seed
            }
        }
    }

    fun completeOnboarding(setupRoute: String? = null) {
        if (setupRoute != null) savedStateHandle[KEY_SETUP_ROUTE] = setupRoute
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    fun setDefaultDestination(destination: UploadDestination) {
        viewModelScope.launch {
            settingsRepository.setDefaultDestination(destination)
        }
    }
}

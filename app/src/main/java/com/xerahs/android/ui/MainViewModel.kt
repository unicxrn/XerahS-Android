package com.xerahs.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.model.BiometricLockMode
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.common.file.MimeTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
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
    val pendingImportUri: StateFlow<String?> = savedStateHandle.getStateFlow<String?>(KEY_IMPORT, null)
    val pendingLaunchCapture: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_CAPTURE, false)
    val pendingDeepLink: StateFlow<String?> = savedStateHandle.getStateFlow<String?>(KEY_DEEP_LINK, null)

    fun clearSharedPaths() { savedStateHandle[KEY_PATHS] = null }
    fun clearImportUri() { savedStateHandle[KEY_IMPORT] = null }
    fun clearLaunchCapture() { savedStateHandle[KEY_CAPTURE] = false }
    fun setImportUri(uri: String) { savedStateHandle[KEY_IMPORT] = uri }
    fun requestLaunchCapture() { savedStateHandle[KEY_CAPTURE] = true }
    fun setDeepLink(uri: String) { savedStateHandle[KEY_DEEP_LINK] = uri }
    fun clearDeepLink() { savedStateHandle[KEY_DEEP_LINK] = null }

    /** Copies a shared item into app storage off the main thread, in a scope that outlives rotation. */
    fun handleSend(uri: Uri?, text: String?) {
        viewModelScope.launch {
            if (uri != null) {
                val (name, path) = withContext(Dispatchers.IO) {
                    val name = displayName(uri)
                    name to if (isUploaderConfig(name)) null else copyUriToInternal(uri, name)
                }
                if (isUploaderConfig(name)) {
                    savedStateHandle[KEY_IMPORT] = uri.toString()
                } else if (path != null) {
                    savedStateHandle[KEY_PATHS] = arrayListOf(path)
                }
            } else if (text != null) {
                withContext(Dispatchers.IO) { writeSharedText(text) }
                    ?.let { savedStateHandle[KEY_PATHS] = arrayListOf(it) }
            }
        }
    }

    fun handleSendMultiple(uris: List<Uri>) {
        viewModelScope.launch {
            val paths = withContext(Dispatchers.IO) {
                uris.mapNotNull { copyUriToInternal(it, displayName(it)) }
            }
            if (paths.isNotEmpty()) savedStateHandle[KEY_PATHS] = ArrayList(paths)
        }
    }

    fun isUploaderConfig(name: String?): Boolean =
        name?.substringAfterLast('.', "")?.lowercase() in setOf("sxcu", "xsdc")

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun newSharedDir(): File =
        File(context.filesDir, "captures/shared_${System.currentTimeMillis()}_${(0..9999).random()}").apply { mkdirs() }

    /** Copies a shared item into app storage, keeping its real name (and so its extension). */
    private fun copyUriToInternal(uri: Uri, displayName: String?): String? {
        val dir = newSharedDir()
        return try {
            copyInto(dir, uri, displayName)
        } catch (e: Exception) {
            dir.deleteRecursively() // don't leave empty share folders behind
            null
        }
    }

    private fun copyInto(dir: File, uri: Uri, displayName: String?): String {
        var name = (displayName ?: "shared").replace(Regex("""[\\/:*?"<>|]"""), "_")
        if (!name.contains('.')) {
            val ext = context.contentResolver.getType(uri)?.let { MimeTypes.extensionFor(it) } ?: "bin"
            name = "$name.$ext"
        }
        val file = File(dir, limitFileName(name))
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { out -> input.copyTo(out) }
        } ?: throw java.io.FileNotFoundException(uri.toString())
        return file.absolutePath
    }

    /** Keeps the name's UTF-8 length within [MAX_NAME_BYTES] by trimming the base name, never the extension. */
    private fun limitFileName(name: String): String {
        val dot = name.lastIndexOf('.')
        val ext = if (dot >= 0) name.substring(dot) else ""
        var base = if (dot >= 0) name.substring(0, dot) else name
        val budget = (MAX_NAME_BYTES - ext.toByteArray(Charsets.UTF_8).size).coerceAtLeast(1)
        while (base.isNotEmpty() && base.toByteArray(Charsets.UTF_8).size > budget) {
            base = base.dropLast(1)
            // Don't leave a dangling high surrogate.
            if (base.isNotEmpty() && base.last().isHighSurrogate()) base = base.dropLast(1)
        }
        if (base.isBlank()) base = "shared"
        return base + ext
    }

    private fun writeSharedText(text: String): String? = runCatching {
        File(newSharedDir(), "shared-text.txt").apply { writeText(text) }.absolutePath
    }.getOrNull()

    private companion object {
        const val KEY_PATHS = "pendingSharedPaths"
        const val KEY_IMPORT = "pendingImportUri"
        const val KEY_CAPTURE = "pendingLaunchCapture"
        const val KEY_DEEP_LINK = "pendingDeepLink"
        const val MAX_NAME_BYTES = 200
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

    fun completeOnboarding() {
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

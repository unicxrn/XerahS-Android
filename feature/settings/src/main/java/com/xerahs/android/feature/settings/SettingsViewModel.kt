package com.xerahs.android.feature.settings

import kotlinx.coroutines.flow.first
import com.xerahs.android.core.domain.model.globalConfigFor
import com.xerahs.android.core.domain.model.isSetupComplete
import com.xerahs.android.core.domain.model.BiometricLockMode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.xerahs.android.core.common.sxcu.CustomDestinationType
import com.xerahs.android.core.domain.model.AfterUploadAction
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.CustomTheme
import com.xerahs.android.core.domain.model.FileKind
import com.xerahs.android.core.domain.model.LinkFormat
import com.xerahs.android.core.domain.model.ImageFormat
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.domain.repository.SettingsRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class SettingsUiState(
    val defaultDestination: UploadDestination = UploadDestination.IMGUR,
    val overlayEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fileNamingPattern: String = "{original}",
    val dynamicColor: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.VIOLET,
    val oledBlack: Boolean = true,
    val screenshotWatch: Boolean = false,
    val screenshotAutoUpload: Boolean = false,
    val screenshotPermissionLost: Boolean = false,
    val imageQuality: Int = 85,
    val maxImageDimension: Int = 0,
    val defaultAfterUploadActions: Set<AfterUploadAction> = emptySet(),
    val biometricLockMode: BiometricLockMode = BiometricLockMode.OFF,
    val uploadFormat: ImageFormat = ImageFormat.ORIGINAL,
    val stripExif: Boolean = false,
    val convertHeicToPng: Boolean = true,
    val autoLockTimeout: Long = 0L,
    val destinationConfigured: Boolean = false,
    val exportImportMessage: String? = null,
    val customThemeId: String? = null,
    val customThemes: List<CustomTheme> = emptyList(),
    val currentAccentSeed: Int? = null,
    val importPreview: ImportPreview? = null,
    val pendingImportJson: String? = null,
    val shortenerProfileId: String? = null,
    val shortenerProfiles: List<UploadProfile> = emptyList(),
    val allProfiles: List<UploadProfile> = emptyList(),
    val routes: Map<FileKind, String?> = emptyMap(),
    val linkFormat: LinkFormat = LinkFormat.PLAIN,
    val backupPassphraseRequest: BackupPassphraseRequest? = null,
    val pendingBackupPayload: String? = null,
    val passphraseError: String? = null
)

enum class BackupPassphraseRequest { EXPORT, IMPORT }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val exportImportManager: ExportImportManager,
    private val profileRepository: UploadProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var exportPassphrase: CharArray? = null
    private var pendingEncryptedBackup: String? = null

    init {
        viewModelScope.launch {
            launch {
                settingsRepository.getDefaultDestination().collect { dest ->
                    _uiState.value = _uiState.value.copy(defaultDestination = dest)
                }
            }
            launch {
                settingsRepository.getOverlayEnabled().collect { enabled ->
                    _uiState.value = _uiState.value.copy(overlayEnabled = enabled)
                }
            }
            launch {
                settingsRepository.getThemeMode().collect { mode ->
                    _uiState.value = _uiState.value.copy(themeMode = mode)
                }
            }
            launch {
                settingsRepository.getFileNamingPattern().collect { pattern ->
                    _uiState.value = _uiState.value.copy(fileNamingPattern = pattern)
                }
            }
            launch {
                settingsRepository.getDynamicColor().collect { enabled ->
                    _uiState.value = _uiState.value.copy(dynamicColor = enabled)
                }
            }
            launch {
                settingsRepository.getColorTheme().collect { theme ->
                    _uiState.value = _uiState.value.copy(colorTheme = theme)
                }
            }
            launch {
                settingsRepository.getOledBlack().collect { enabled ->
                    _uiState.value = _uiState.value.copy(oledBlack = enabled)
                }
            }
            launch {
                settingsRepository.getScreenshotWatchEnabled().collect { enabled ->
                    _uiState.value = _uiState.value.copy(screenshotWatch = enabled)
                }
            }
            launch {
                settingsRepository.getScreenshotPermissionLost().collect { lost ->
                    _uiState.value = _uiState.value.copy(screenshotPermissionLost = lost)
                }
            }
            launch {
                settingsRepository.getScreenshotAutoUpload().collect { enabled ->
                    _uiState.value = _uiState.value.copy(screenshotAutoUpload = enabled)
                }
            }
            launch {
                settingsRepository.getImageQuality().collect { quality ->
                    _uiState.value = _uiState.value.copy(imageQuality = quality)
                }
            }
            launch {
                settingsRepository.getMaxImageDimension().collect { maxDim ->
                    _uiState.value = _uiState.value.copy(maxImageDimension = maxDim)
                }
            }
            launch {
                settingsRepository.getDefaultAfterUploadActions().collect { actions ->
                    _uiState.value = _uiState.value.copy(defaultAfterUploadActions = actions)
                }
            }
            launch {
                settingsRepository.getBiometricLockMode().collect { mode ->
                    _uiState.value = _uiState.value.copy(biometricLockMode = BiometricLockMode.fromName(mode))
                }
            }
            launch {
                settingsRepository.getUploadFormat().collect { format ->
                    _uiState.value = _uiState.value.copy(uploadFormat = format)
                }
            }
            launch {
                settingsRepository.getStripExif().collect { enabled ->
                    _uiState.value = _uiState.value.copy(stripExif = enabled)
                }
            }
            launch {
                settingsRepository.getConvertHeicToPng().collect { enabled ->
                    _uiState.value = _uiState.value.copy(convertHeicToPng = enabled)
                }
            }
            launch {
                settingsRepository.getAutoLockTimeout().collect { timeout ->
                    _uiState.value = _uiState.value.copy(autoLockTimeout = timeout)
                }
            }
            launch {
                settingsRepository.getDefaultDestination().collect { dest ->
                    val configured = checkDestinationConfigured(dest)
                    _uiState.value = _uiState.value.copy(destinationConfigured = configured)
                }
            }
            launch {
                // Combine the selected id with the themes table so the selected-swatch
                // indicator updates when the reusable accent entity's seed changes (same id).
                combine(
                    settingsRepository.getCustomThemeId(),
                    settingsRepository.getAllCustomThemes()
                ) { id, themes ->
                    val seed = if (id != null) themes.firstOrNull { it.id == id }?.seedColor else null
                    id to seed
                }.collect { (id, seed) ->
                    _uiState.value = _uiState.value.copy(
                        customThemeId = id,
                        currentAccentSeed = seed
                    )
                }
            }
            launch {
                settingsRepository.getAllCustomThemes().collect { themes ->
                    _uiState.value = _uiState.value.copy(customThemes = themes)
                }
            }
            launch {
                profileRepository.getAllProfiles().collect { profiles ->
                    _uiState.value = _uiState.value.copy(allProfiles = profiles)
                }
            }
            FileKind.entries.forEach { kind ->
                launch {
                    settingsRepository.getRouteProfileId(kind).collect { id ->
                        _uiState.value = _uiState.value.copy(routes = _uiState.value.routes + (kind to id))
                    }
                }
            }
            launch {
                settingsRepository.getLinkFormat().collect { format ->
                    _uiState.value = _uiState.value.copy(linkFormat = format)
                }
            }
            launch {
                settingsRepository.getShortenerProfileId().collect { id ->
                    _uiState.value = _uiState.value.copy(shortenerProfileId = id)
                }
            }
            launch {
                profileRepository.getProfilesForDestination(UploadDestination.CUSTOM_HTTP).collect { profiles ->
                    val shorteners = withContext(Dispatchers.IO) {
                        profiles.filter { p ->
                            (profileRepository.getProfileConfig(p.id, UploadDestination.CUSTOM_HTTP) as? UploadConfig.CustomUploaderConfig)
                                ?.spec?.destinationTypes?.contains(CustomDestinationType.URL_SHORTENER) == true
                        }
                    }
                    _uiState.value = _uiState.value.copy(shortenerProfiles = shorteners)
                }
            }
        }
    }

    private suspend fun checkDestinationConfigured(destination: UploadDestination): Boolean {
        if (destination == UploadDestination.LOCAL) return true
        return withContext(Dispatchers.IO) {
            val profiles = profileRepository.getProfilesForDestination(destination).first()
            isSetupComplete(
                settingsRepository.globalConfigFor(destination),
                profiles.map { profileRepository.getProfileConfig(it.id, destination) }
            )
        }
    }

    fun setDefaultDestination(destination: UploadDestination) {
        viewModelScope.launch {
            settingsRepository.setDefaultDestination(destination)
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOverlayEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setFileNamingPattern(pattern: String) {
        viewModelScope.launch {
            settingsRepository.setFileNamingPattern(pattern)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setColorTheme(theme: ColorTheme) {
        viewModelScope.launch {
            settingsRepository.setColorTheme(theme)
        }
    }

    fun setOledBlack(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOledBlack(enabled)
        }
    }

    fun setScreenshotWatch(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setScreenshotWatchEnabled(enabled) }
    }

    fun setScreenshotAutoUpload(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setScreenshotAutoUpload(enabled) }
    }

    fun setImageQuality(quality: Int) {
        viewModelScope.launch {
            settingsRepository.setImageQuality(quality)
        }
    }

    fun setMaxImageDimension(maxDim: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxImageDimension(maxDim)
        }
    }

    fun toggleDefaultAfterUploadAction(action: AfterUploadAction) {
        viewModelScope.launch {
            val current = _uiState.value.defaultAfterUploadActions
            settingsRepository.setDefaultAfterUploadActions(if (action in current) current - action else current + action)
        }
    }

    fun setBiometricLockMode(mode: BiometricLockMode) {
        viewModelScope.launch {
            settingsRepository.setBiometricLockMode(mode.name)
        }
    }

    fun setUploadFormat(format: ImageFormat) {
        viewModelScope.launch {
            settingsRepository.setUploadFormat(format)
        }
    }

    fun setStripExif(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStripExif(enabled)
        }
    }

    fun setConvertHeicToPng(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setConvertHeicToPng(enabled) }
    }

    fun setAutoLockTimeout(timeout: Long) {
        viewModelScope.launch {
            settingsRepository.setAutoLockTimeout(timeout)
        }
    }

    fun selectCustomTheme(themeId: String) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(false)
            settingsRepository.setCustomThemeId(themeId)
        }
    }

    fun clearCustomTheme() {
        viewModelScope.launch {
            settingsRepository.setCustomThemeId(null)
        }
    }

    /**
     * Sets the active accent seed color (ARGB) and applies it live across the app.
     *
     * The contrast-safe color engine (Phase 1) derives a legible scheme from any seed.
     * The seed is persisted as a single reusable [CustomTheme] entity (stable id), which
     * [com.xerahs.android.ui.MainViewModel.customThemeSeedColor] resolves and feeds into the theme.
     */
    fun setAccentSeed(argb: Int) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(false)
            settingsRepository.saveCustomTheme(
                CustomTheme(id = ACCENT_THEME_ID, name = "Accent", seedColor = argb)
            )
            settingsRepository.setCustomThemeId(ACCENT_THEME_ID)
        }
    }

    fun clearAccentSeed() {
        viewModelScope.launch {
            settingsRepository.setCustomThemeId(null)
        }
    }

    fun saveCustomTheme(theme: CustomTheme) {
        viewModelScope.launch {
            settingsRepository.saveCustomTheme(theme)
        }
    }

    fun deleteCustomTheme(themeId: String) {
        viewModelScope.launch {
            if (_uiState.value.customThemeId == themeId) {
                settingsRepository.setCustomThemeId(null)
            }
            settingsRepository.deleteCustomTheme(themeId)
        }
    }

    fun requestExportPassphrase() {
        _uiState.value = _uiState.value.copy(backupPassphraseRequest = BackupPassphraseRequest.EXPORT)
    }

    fun setExportPassphrase(passphrase: CharArray) {
        exportPassphrase = passphrase
        _uiState.value = _uiState.value.copy(backupPassphraseRequest = null)
    }

    fun exportBackup(outputStream: OutputStream) {
        val passphrase = exportPassphrase ?: return
        exportPassphrase = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val text = exportImportManager.exportBackup(passphrase)
                    withContext(Dispatchers.IO) { outputStream.use { it.write(text.toByteArray()) } }
                }
                _uiState.value = _uiState.value.copy(exportImportMessage = "Backup saved")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportImportMessage = "Backup failed: ${e.message}")
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun previewImport(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    inputStream.use { it.bufferedReader().readText() }
                }
                if (exportImportManager.isEncryptedBackup(json)) {
                    pendingEncryptedBackup = json
                    _uiState.value = _uiState.value.copy(backupPassphraseRequest = BackupPassphraseRequest.IMPORT)
                    return@launch
                }
                val preview = exportImportManager.parseImportPreview(json)
                _uiState.value = _uiState.value.copy(
                    importPreview = preview,
                    pendingImportJson = json
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportImportMessage = "Import failed: ${e.message}")
            }
        }
    }

    fun unlockBackup(passphrase: CharArray) {
        val text = pendingEncryptedBackup ?: return
        viewModelScope.launch {
            try {
                val payload = withContext(Dispatchers.Default) { exportImportManager.openBackup(text, passphrase) }
                val settingsJson = payload.get("settings").toString()
                val preview = exportImportManager.parseImportPreview(settingsJson)
                val extras = exportImportManager.parseBackupExtrasPreview(payload)
                pendingEncryptedBackup = null
                _uiState.value = _uiState.value.copy(
                    backupPassphraseRequest = null,
                    passphraseError = null,
                    importPreview = ImportPreview(preview.sections + listOfNotNull(extras)),
                    pendingImportJson = settingsJson,
                    pendingBackupPayload = payload.toString()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(passphraseError = "Wrong passphrase or damaged backup")
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun cancelPassphrase() {
        exportPassphrase?.fill('\u0000')
        exportPassphrase = null
        pendingEncryptedBackup = null
        _uiState.value = _uiState.value.copy(backupPassphraseRequest = null, passphraseError = null)
    }

    fun reportExportError(message: String) {
        _uiState.value = _uiState.value.copy(exportImportMessage = message)
    }

    fun applyResolvedImport() {
        val preview = _uiState.value.importPreview ?: return
        val json = _uiState.value.pendingImportJson ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    exportImportManager.applyResolvedImport(json, preview)
                    _uiState.value.pendingBackupPayload?.let {
                        exportImportManager.applyBackupExtras(JsonParser.parseString(it).asJsonObject, preview)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    importPreview = null,
                    pendingImportJson = null,
                    pendingBackupPayload = null,
                    exportImportMessage = "Settings imported successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    importPreview = null,
                    pendingImportJson = null,
                    pendingBackupPayload = null,
                    exportImportMessage = "Import failed: ${e.message}"
                )
            }
        }
    }

    fun cancelImportPreview() {
        _uiState.value = _uiState.value.copy(
            importPreview = null,
            pendingImportJson = null,
            pendingBackupPayload = null
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(exportImportMessage = null)
    }

    fun setRouteProfileId(kind: FileKind, profileId: String?) {
        viewModelScope.launch { settingsRepository.setRouteProfileId(kind, profileId) }
    }

    fun setLinkFormat(format: LinkFormat) {
        viewModelScope.launch { settingsRepository.setLinkFormat(format) }
    }

    fun setShortenerProfileId(id: String?) {
        viewModelScope.launch { settingsRepository.setShortenerProfileId(id) }
    }

    companion object {
        /** Stable id for the single reusable "active accent" custom theme entity. */
        const val ACCENT_THEME_ID = "__active_accent__"
    }
}

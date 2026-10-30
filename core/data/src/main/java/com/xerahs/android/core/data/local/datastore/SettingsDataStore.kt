package com.xerahs.android.core.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xerahs.android.core.common.image.ColorFormat
import com.xerahs.android.core.domain.model.AfterUploadAction
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.FileKind
import com.xerahs.android.core.domain.model.ImageFormat
import com.xerahs.android.core.domain.model.LinkFormat
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadDestination
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DEFAULT_DESTINATION = stringPreferencesKey("default_destination")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FILE_NAMING_PATTERN = stringPreferencesKey("file_naming_pattern")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val OLED_BLACK = booleanPreferencesKey("oled_black")
        val SCREENSHOT_WATCH_ENABLED = booleanPreferencesKey("screenshot_watch_enabled")
        val SCREENSHOT_AUTO_UPLOAD = booleanPreferencesKey("screenshot_auto_upload")
        val LAST_SCREENSHOT_CHECK = longPreferencesKey("last_screenshot_check")
        val SCREENSHOT_SEEN_IDS = stringPreferencesKey("screenshot_seen_ids")
        val SCREENSHOT_PERMISSION_LOST = booleanPreferencesKey("screenshot_permission_lost")
        val IMAGE_QUALITY = intPreferencesKey("image_quality")
        val MAX_IMAGE_DIMENSION = intPreferencesKey("max_image_dimension")
        val AUTO_COPY_URL = booleanPreferencesKey("auto_copy_url")
        val BIOMETRIC_LOCK_MODE = stringPreferencesKey("biometric_lock_mode")
        val UPLOAD_FORMAT = stringPreferencesKey("upload_format")
        val STRIP_EXIF = booleanPreferencesKey("strip_exif")
        val AUTO_LOCK_TIMEOUT = longPreferencesKey("auto_lock_timeout")
        val CUSTOM_THEME_ID = stringPreferencesKey("custom_theme_id")
        val SHORTENER_PROFILE_ID = stringPreferencesKey("shortener_profile_id")
        val CONVERT_HEIC = booleanPreferencesKey("convert_heic_to_png")
        val DEFAULT_AFTER_UPLOAD_ACTIONS = stringPreferencesKey("after_upload_actions")
        val RECENT_COLORS = stringPreferencesKey("recent_colors")
        val PINNED_HOST_KEYS = stringPreferencesKey("pinned_host_keys")
        val LINK_FORMAT = stringPreferencesKey("link_format")
    }

    // SSH host-key pins, one `host:port=SHA256:...` per line. Fingerprints aren't secrets.
    suspend fun getPinnedHostKeys(): String? = context.dataStore.data.first()[Keys.PINNED_HOST_KEYS]

    suspend fun updatePinnedHostKeys(transform: (String?) -> String?) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs[Keys.PINNED_HOST_KEYS])
            if (next.isNullOrEmpty()) prefs.remove(Keys.PINNED_HOST_KEYS) else prefs[Keys.PINNED_HOST_KEYS] = next
        }
    }

    private fun routeKey(kind: FileKind) = stringPreferencesKey(
        when (kind) {
            FileKind.IMAGE -> "route_image"
            FileKind.TEXT -> "route_text"
            FileKind.OTHER -> "route_other"
        }
    )

    fun getRouteProfileId(kind: FileKind): Flow<String?> = context.dataStore.data.map { it[routeKey(kind)] }

    suspend fun setRouteProfileId(kind: FileKind, profileId: String?) {
        context.dataStore.edit { prefs ->
            if (profileId == null) prefs.remove(routeKey(kind)) else prefs[routeKey(kind)] = profileId
        }
    }

    /** Drops every route that points at [profileId], so those files go to Default again. */
    suspend fun clearRoutesTo(profileId: String) {
        context.dataStore.edit { prefs ->
            FileKind.entries.map(::routeKey).filter { prefs[it] == profileId }.forEach { prefs.remove(it) }
        }
    }

    fun getLinkFormat(): Flow<LinkFormat> = context.dataStore.data.map { LinkFormat.fromName(it[Keys.LINK_FORMAT]) }

    suspend fun setLinkFormat(format: LinkFormat) {
        context.dataStore.edit { it[Keys.LINK_FORMAT] = format.name }
    }

    private fun profileActionsKey(profileId: String) = stringPreferencesKey("after_upload_actions_$profileId")

    // Missing key: fall back to the old auto-copy switch.
    fun getDefaultAfterUploadActions(): Flow<Set<AfterUploadAction>> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_AFTER_UPLOAD_ACTIONS]?.let { AfterUploadAction.decode(it) }
            ?: if (prefs[Keys.AUTO_COPY_URL] == true) setOf(AfterUploadAction.COPY_URL) else emptySet()
    }

    suspend fun setDefaultAfterUploadActions(actions: Set<AfterUploadAction>) {
        context.dataStore.edit { it[Keys.DEFAULT_AFTER_UPLOAD_ACTIONS] = AfterUploadAction.encode(actions) }
    }

    // null = the profile uses the default set.
    fun getProfileAfterUploadActions(profileId: String): Flow<Set<AfterUploadAction>?> = context.dataStore.data.map { prefs ->
        prefs[profileActionsKey(profileId)]?.let { AfterUploadAction.decode(it) }
    }

    suspend fun setProfileAfterUploadActions(profileId: String, actions: Set<AfterUploadAction>?) {
        context.dataStore.edit { prefs ->
            if (actions == null) prefs.remove(profileActionsKey(profileId))
            else prefs[profileActionsKey(profileId)] = AfterUploadAction.encode(actions)
        }
    }

    fun getDefaultDestination(): Flow<UploadDestination> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.DEFAULT_DESTINATION] ?: UploadDestination.IMGUR.name
        UploadDestination.valueOf(name)
    }

    suspend fun setDefaultDestination(destination: UploadDestination) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_DESTINATION] = destination.name
        }
    }

    fun getOverlayEnabled(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.OVERLAY_ENABLED] ?: false
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OVERLAY_ENABLED] = enabled
        }
    }

    fun getThemeMode(): Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    fun getFileNamingPattern(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.FILE_NAMING_PATTERN] ?: "{original}"
    }

    suspend fun setFileNamingPattern(pattern: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FILE_NAMING_PATTERN] = pattern
        }
    }

    fun getOnboardingCompleted(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    fun getDynamicColor(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: false
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    fun getColorTheme(): Flow<ColorTheme> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.COLOR_THEME] ?: ColorTheme.VIOLET.name
        try {
            ColorTheme.valueOf(name)
        } catch (e: IllegalArgumentException) {
            ColorTheme.VIOLET
        }
    }

    suspend fun setColorTheme(theme: ColorTheme) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COLOR_THEME] = theme.name
        }
    }

    fun getOledBlack(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        // Default on so a fresh install matches the pure-black brand look. Users can
        // turn it off for a softer dim dark theme.
        prefs[Keys.OLED_BLACK] ?: true
    }

    suspend fun setOledBlack(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OLED_BLACK] = enabled
        }
    }

    fun getImageQuality(): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.IMAGE_QUALITY] ?: 85
    }

    suspend fun setImageQuality(quality: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IMAGE_QUALITY] = quality
        }
    }

    fun getMaxImageDimension(): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.MAX_IMAGE_DIMENSION] ?: 0
    }

    suspend fun setMaxImageDimension(maxDim: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_IMAGE_DIMENSION] = maxDim
        }
    }

    fun getBiometricLockMode(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.BIOMETRIC_LOCK_MODE] ?: "OFF"
    }

    suspend fun setBiometricLockMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_LOCK_MODE] = mode
        }
    }

    fun getUploadFormat(): Flow<ImageFormat> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.UPLOAD_FORMAT] ?: ImageFormat.ORIGINAL.name
        try {
            ImageFormat.valueOf(name)
        } catch (e: IllegalArgumentException) {
            ImageFormat.ORIGINAL
        }
    }

    suspend fun setUploadFormat(format: ImageFormat) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UPLOAD_FORMAT] = format.name
        }
    }

    fun getStripExif(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.STRIP_EXIF] ?: false
    }

    suspend fun setStripExif(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STRIP_EXIF] = enabled
        }
    }

    fun getAutoLockTimeout(): Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_LOCK_TIMEOUT] ?: 0L
    }

    suspend fun setAutoLockTimeout(timeout: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_LOCK_TIMEOUT] = timeout
        }
    }

    fun getCustomThemeId(): Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_THEME_ID]
    }

    suspend fun setCustomThemeId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id != null) {
                prefs[Keys.CUSTOM_THEME_ID] = id
            } else {
                prefs.remove(Keys.CUSTOM_THEME_ID)
            }
        }
    }

    fun getShortenerProfileId(): Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHORTENER_PROFILE_ID]
    }

    suspend fun setShortenerProfileId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id != null) prefs[Keys.SHORTENER_PROFILE_ID] = id else prefs.remove(Keys.SHORTENER_PROFILE_ID)
        }
    }

    fun getConvertHeicToPng(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.CONVERT_HEIC] ?: true
    }

    suspend fun setConvertHeicToPng(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.CONVERT_HEIC] = enabled }
    }

    fun getRecentColors(): Flow<List<Int>> = context.dataStore.data.map { prefs ->
        prefs[Keys.RECENT_COLORS].orEmpty().split(',').mapNotNull { ColorFormat.parseHex(it) }
    }

    suspend fun addRecentColor(color: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_COLORS].orEmpty().split(',').filter { it.isNotBlank() }
            prefs[Keys.RECENT_COLORS] = (listOf(ColorFormat.hex(color)) + current)
                .distinct().take(10).joinToString(",")
        }
    }

    fun getScreenshotWatchEnabled(): Flow<Boolean> = context.dataStore.data.map { it[Keys.SCREENSHOT_WATCH_ENABLED] ?: false }

    /** Turning it on also resets the check time to now, so older screenshots are ignored. */
    suspend fun setScreenshotWatchEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCREENSHOT_WATCH_ENABLED] = enabled
            if (enabled) {
                prefs[Keys.LAST_SCREENSHOT_CHECK] = System.currentTimeMillis() / 1000
                prefs[Keys.SCREENSHOT_PERMISSION_LOST] = false
            }
        }
    }

    fun getScreenshotAutoUpload(): Flow<Boolean> = context.dataStore.data.map { it[Keys.SCREENSHOT_AUTO_UPLOAD] ?: false }

    suspend fun setScreenshotAutoUpload(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCREENSHOT_AUTO_UPLOAD] = enabled }
    }

    /** Seconds since the epoch, as in MediaStore DATE_ADDED. */
    suspend fun getLastScreenshotCheck(): Long = context.dataStore.data.first()[Keys.LAST_SCREENSHOT_CHECK] ?: 0L

    suspend fun setLastScreenshotCheck(seconds: Long) {
        context.dataStore.edit { it[Keys.LAST_SCREENSHOT_CHECK] = seconds }
    }

    /** Comma separated MediaStore ids the watcher has already handled (newest last). */
    suspend fun getScreenshotSeenIds(): String = context.dataStore.data.first()[Keys.SCREENSHOT_SEEN_IDS] ?: ""

    suspend fun setScreenshotSeenIds(csv: String) {
        context.dataStore.edit { it[Keys.SCREENSHOT_SEEN_IDS] = csv }
    }

    /** True when the watcher turned itself off because photo access was revoked. */
    fun getScreenshotPermissionLost(): Flow<Boolean> = context.dataStore.data.map { it[Keys.SCREENSHOT_PERMISSION_LOST] ?: false }

    suspend fun setScreenshotPermissionLost(lost: Boolean) {
        context.dataStore.edit { it[Keys.SCREENSHOT_PERMISSION_LOST] = lost }
    }
}

package com.xerahs.android.core.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.domain.model.UploadDestination
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
        val IMAGE_QUALITY = intPreferencesKey("image_quality")
        val MAX_IMAGE_DIMENSION = intPreferencesKey("max_image_dimension")
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
        prefs[Keys.DYNAMIC_COLOR] ?: true
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
        prefs[Keys.OLED_BLACK] ?: false
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
}

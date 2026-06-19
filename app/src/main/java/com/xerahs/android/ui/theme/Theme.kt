package com.xerahs.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.ThemeMode

@Composable
fun XerahSTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    colorTheme: ColorTheme = ColorTheme.VIOLET,
    oledBlack: Boolean = false,
    customThemeSeedColor: Int? = null,
    accentSeed: Int = SIGNAL_LIME,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Dynamic color (Material You) wins when enabled, even if a custom accent is stored.
    // Otherwise use the custom accent if set, else the default accent. The True black
    // toggle (oledBlack) controls whether dark surfaces are pure black or a soft dim.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        customThemeSeedColor != null -> colorSchemeForAccent(customThemeSeedColor, darkTheme, oledBlack)
        else -> colorSchemeForAccent(accentSeed, darkTheme, oledBlack)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = XerahSTypography,
        shapes = XerahSShapes,
        content = content
    )
}

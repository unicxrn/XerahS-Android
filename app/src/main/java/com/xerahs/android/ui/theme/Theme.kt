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
    trueBlack: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        customThemeSeedColor != null -> colorSchemeForAccent(customThemeSeedColor, darkTheme, trueBlack)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> colorSchemeForAccent(accentSeed, darkTheme, trueBlack)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = XerahSTypography,
        shapes = XerahSShapes,
        content = content
    )
}

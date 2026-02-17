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
    dynamicColor: Boolean = true,
    colorTheme: ColorTheme = ColorTheme.VIOLET,
    oledBlack: Boolean = false,
    customThemeSeedColor: Int? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        customThemeSeedColor != null -> {
            generateColorSchemeFromSeed(customThemeSeedColor, darkTheme, oledBlack)
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (oledBlack && darkTheme) {
                scheme.copy(
                    background = androidx.compose.ui.graphics.Color.Black,
                    surface = androidx.compose.ui.graphics.Color.Black,
                    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF050505),
                    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
                    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF121212),
                )
            } else {
                scheme
            }
        }
        else -> colorSchemeForTheme(colorTheme, darkTheme, oledBlack)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = XerahSTypography,
        shapes = XerahSShapes,
        content = content
    )
}

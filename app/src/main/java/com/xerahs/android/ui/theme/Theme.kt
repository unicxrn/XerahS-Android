package com.xerahs.android.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.xerahs.android.core.common.theme.LumenMode
import com.xerahs.android.core.common.theme.LumenPalette
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.ui.lumen.LocalLumen
import com.xerahs.android.core.ui.lumen.LumenTokens

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

    val mode = when {
        !darkTheme -> LumenMode.LIGHT
        oledBlack -> LumenMode.TRUE_BLACK
        else -> LumenMode.DARK
    }
    // Derived from the final primary so Material You and custom seeds get the same extras.
    val roles = LumenPalette.derive(colorScheme.primary.toArgb(), mode)
    val tokens = LumenTokens(
        tint = colorScheme.primaryContainer,
        ink = colorScheme.onPrimaryContainer,
        hairline = Color(roles.hairline),
        glow = Color(roles.glow),
        navContainer = Color(roles.navContainer),
        shadow = Color(roles.shadow),
        isDark = darkTheme,
        display = InterTightFamily,
        mono = JetBrainsMonoFamily,
    )

    // enableEdgeToEdge follows the system night mode; match bar icons to the app's own theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalLumen provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = XerahSTypography,
            shapes = XerahSShapes,
            content = content
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

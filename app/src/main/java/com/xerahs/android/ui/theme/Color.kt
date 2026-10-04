package com.xerahs.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.xerahs.android.core.common.theme.LumenMode
import com.xerahs.android.core.common.theme.LumenPalette

/** The default signal accent (lime). Users can override the seed. */
const val SIGNAL_LIME: Int = 0xFFB8F23A.toInt()

/**
 * Build the Lumen Material 3 scheme from a user accent [seedArgb]: accent-tinted neutrals,
 * contrast-safe text. See [LumenPalette].
 */
fun colorSchemeForAccent(seedArgb: Int, dark: Boolean, trueBlack: Boolean = true): ColorScheme {
    val mode = when {
        !dark -> LumenMode.LIGHT
        trueBlack -> LumenMode.TRUE_BLACK
        else -> LumenMode.DARK
    }
    val r = LumenPalette.derive(seedArgb, mode)
    fun c(argb: Int) = Color(argb)
    return if (dark) {
        darkColorScheme(
            primary = c(r.accent), onPrimary = c(r.onAccent),
            primaryContainer = c(r.tint), onPrimaryContainer = c(r.ink),
            secondary = c(r.accent), onSecondary = c(r.onAccent),
            secondaryContainer = c(r.tint), onSecondaryContainer = c(r.ink),
            tertiary = c(r.ink), onTertiary = c(r.background),
            background = c(r.background), onBackground = c(r.onSurface),
            surface = c(r.surface), onSurface = c(r.onSurface),
            surfaceVariant = c(r.surfaceContainer), onSurfaceVariant = c(r.onSurfaceVariant),
            surfaceContainerLowest = c(r.background),
            surfaceContainerLow = c(r.surfaceContainerLow),
            surfaceContainer = c(r.surfaceContainer),
            surfaceContainerHigh = c(r.surfaceContainerHigh),
            surfaceContainerHighest = c(r.surfaceContainerHighest),
            outline = c(r.outline), outlineVariant = c(r.outlineVariant),
        )
    } else {
        lightColorScheme(
            primary = c(r.accent), onPrimary = c(r.onAccent),
            primaryContainer = c(r.tint), onPrimaryContainer = c(r.ink),
            secondary = c(r.accent), onSecondary = c(r.onAccent),
            secondaryContainer = c(r.tint), onSecondaryContainer = c(r.ink),
            tertiary = c(r.ink), onTertiary = Color.White,
            background = c(r.background), onBackground = c(r.onSurface),
            surface = c(r.surface), onSurface = c(r.onSurface),
            surfaceVariant = c(r.surfaceContainer), onSurfaceVariant = c(r.onSurfaceVariant),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = c(r.surfaceContainerLow),
            surfaceContainer = c(r.surfaceContainer),
            surfaceContainerHigh = c(r.surfaceContainerHigh),
            surfaceContainerHighest = c(r.surfaceContainerHighest),
            outline = c(r.outline), outlineVariant = c(r.outlineVariant),
        )
    }
}

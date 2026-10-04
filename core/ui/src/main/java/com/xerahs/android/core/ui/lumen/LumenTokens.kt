package com.xerahs.android.core.ui.lumen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Lumen extras that Material 3's ColorScheme has no slot for. Provided by XerahSTheme. */
@Immutable
data class LumenTokens(
    val tint: Color,
    val ink: Color,
    val hairline: Color,
    val glow: Color,
    val navContainer: Color,
    val shadow: Color,
    val isDark: Boolean,
    val display: FontFamily,
    val mono: FontFamily,
)

val LocalLumen = staticCompositionLocalOf {
    LumenTokens(
        tint = Color(0xFFEDE7FD), ink = Color(0xFF3A1C8C), hairline = Color(0x12131218),
        glow = Color(0x4D6D3BF0), navContainer = Color(0xEBFFFFFF), shadow = Color(0x59131218),
        isDark = false, display = FontFamily.Default, mono = FontFamily.Monospace,
    )
}

object Lumen {
    val tokens: LumenTokens
        @Composable @ReadOnlyComposable get() = LocalLumen.current
}

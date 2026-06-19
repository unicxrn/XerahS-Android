package com.xerahs.android.core.common.theme

import kotlin.math.pow

/** Roles derived from a user accent seed, guaranteed legible. ARGB ints (0xAARRGGBB). */
data class AccentRoles(
    val accent: Int,      // fill / focus / selection
    val onAccent: Int,    // text/icon ON the accent fill
    val accentText: Int,  // accent used AS text on the canvas (darkened in light mode)
)

/** Pure-Kotlin WCAG colour math. No Android dependency, so it is plain-JUnit testable. */
object AccentDerivation {

    private fun r(argb: Int) = (argb ushr 16) and 0xFF
    private fun g(argb: Int) = (argb ushr 8) and 0xFF
    private fun b(argb: Int) = argb and 0xFF
    private fun argb(r: Int, g: Int, b: Int) =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance, 0.0 (black) .. 1.0 (white). */
    fun relativeLuminance(c: Int): Double =
        0.2126 * channel(r(c)) + 0.7152 * channel(g(c)) + 0.0722 * channel(b(c))

    /** WCAG contrast ratio, 1.0 .. 21.0. */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private val WHITE = argb(255, 255, 255)
    private val BLACK = argb(0, 0, 0)

    /** Pick black or white for text ON [bg], whichever has more contrast. */
    fun onColorFor(bg: Int): Int =
        if (contrastRatio(bg, WHITE) >= contrastRatio(bg, BLACK)) WHITE else BLACK

    /** Scale a colour toward black until it hits [target] contrast against [against] (cap 24 steps). */
    fun darkenToContrast(seed: Int, against: Int, target: Double): Int {
        var rr = r(seed); var gg = g(seed); var bb = b(seed)
        var out = argb(rr, gg, bb)
        var i = 0
        while (contrastRatio(out, against) < target && i < 24) {
            rr = (rr * 0.85).toInt(); gg = (gg * 0.85).toInt(); bb = (bb * 0.85).toInt()
            out = argb(rr, gg, bb)
            i++
        }
        return out
    }

    /** Derive legible roles from a user accent seed for the given mode. */
    fun deriveAccent(seed: Int, dark: Boolean): AccentRoles =
        if (dark) {
            AccentRoles(accent = seed, onAccent = onColorFor(seed), accentText = seed)
        } else {
            val text = darkenToContrast(seed, WHITE, 4.5)
            AccentRoles(accent = text, onAccent = WHITE, accentText = text)
        }
}

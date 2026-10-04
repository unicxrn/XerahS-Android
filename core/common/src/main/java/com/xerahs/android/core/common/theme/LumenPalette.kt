package com.xerahs.android.core.common.theme

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

enum class LumenMode { LIGHT, DARK, TRUE_BLACK }

/** All Lumen colour roles for one accent seed and mode. ARGB ints. */
data class LumenRoles(
    val accent: Int,
    val onAccent: Int,
    val ink: Int,               // accent used as text/icon on surfaces and on [tint]
    val tint: Int,              // accent-tinted container: chips, selected rows, icon tiles
    val background: Int,
    val surface: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val hairline: Int,          // translucent, for 1dp outlines
    val glow: Int,              // translucent accent, for radial glows
    val navContainer: Int,      // translucent nav pill fill
    val shadow: Int,            // shadow colour for soft elevation
)

/** OkLab colour mixing (same maths as CSS color-mix(in oklab, ...)) and Lumen role derivation. */
object LumenPalette {

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    private fun toLinear(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun toSrgb(c: Double): Int {
        val v = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
        return (v.coerceIn(0.0, 1.0) * 255.0).roundToInt()
    }

    private fun toOklab(argb: Int): DoubleArray {
        val r = toLinear((argb ushr 16) and 0xFF)
        val g = toLinear((argb ushr 8) and 0xFF)
        val b = toLinear(argb and 0xFF)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private fun fromOklab(lab: DoubleArray): Int {
        val l = (lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2]).pow(3)
        val m = (lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2]).pow(3)
        val s = (lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2]).pow(3)
        val r = toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s)
        val g = toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s)
        val b = toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Mix [amount] (0..1) of [color] into [base], like color-mix(in oklab, color X%, base). */
    fun mix(color: Int, amount: Float, base: Int): Int {
        val t = amount.coerceIn(0f, 1f).toDouble()
        val a = toOklab(color)
        val b = toOklab(base)
        return fromOklab(DoubleArray(3) { b[it] + (a[it] - b[it]) * t })
    }

    fun withAlpha(argb: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (argb and 0xFFFFFF)

    /** Mix white into [color] until it reaches [target] contrast against [against]. */
    private fun lightenToContrast(color: Int, against: Int, target: Double): Int {
        var out = color
        var i = 0
        while (AccentDerivation.contrastRatio(out, against) < target && i < 24) {
            out = mix(WHITE, 0.15f, out)
            i++
        }
        return out
    }

    fun derive(seed: Int, mode: LumenMode): LumenRoles = when (mode) {
        LumenMode.LIGHT -> light(seed)
        LumenMode.DARK -> dark(seed, trueBlack = false)
        LumenMode.TRUE_BLACK -> dark(seed, trueBlack = true)
    }

    private fun light(seed: Int): LumenRoles {
        val accent = AccentDerivation.deriveAccent(seed, dark = false).accent
        val tint = mix(accent, 0.13f, WHITE)
        val ink = AccentDerivation.darkenToContrast(mix(accent, 0.82f, BLACK), tint, 4.5)
        val variant = 0xFF62606C.toInt()
        return LumenRoles(
            accent = accent,
            onAccent = WHITE,
            ink = ink,
            tint = tint,
            background = mix(accent, 0.05f, 0xFFF5F5F8.toInt()),
            surface = WHITE,
            surfaceContainerLow = mix(accent, 0.04f, WHITE),
            surfaceContainer = mix(accent, 0.07f, WHITE),
            surfaceContainerHigh = mix(accent, 0.10f, 0xFFF7F7FA.toInt()),
            surfaceContainerHighest = mix(accent, 0.13f, 0xFFF1F1F5.toInt()),
            onSurface = 0xFF131218.toInt(),
            onSurfaceVariant = variant,
            outline = mix(accent, 0.10f, 0xFFC7C7CF.toInt()),
            outlineVariant = mix(accent, 0.06f, 0xFFE4E3EA.toInt()),
            hairline = withAlpha(0xFF131218.toInt(), 0.07f),
            glow = withAlpha(accent, 0.30f),
            navContainer = withAlpha(WHITE, 0.92f),
            shadow = withAlpha(mix(accent, 0.35f, 0xFF131218.toInt()), 0.35f),
        )
    }

    private fun dark(seed: Int, trueBlack: Boolean): LumenRoles {
        val base = if (trueBlack) BLACK else 0xFF0D0D11.toInt()
        val s1 = if (trueBlack) 0xFF0B0B0E.toInt() else 0xFF16161B.toInt()
        val s2 = if (trueBlack) 0xFF111115.toInt() else 0xFF1C1C22.toInt()
        val surface = mix(seed, if (trueBlack) 0.05f else 0.06f, s1)
        val tint = mix(seed, if (trueBlack) 0.20f else 0.24f, s1)
        val ink = lightenToContrast(mix(seed, 0.58f, WHITE), tint, 4.5)
        return LumenRoles(
            accent = seed,
            onAccent = AccentDerivation.onColorFor(seed),
            ink = ink,
            tint = tint,
            background = if (trueBlack) BLACK else mix(seed, 0.06f, base),
            surface = surface,
            surfaceContainerLow = mix(seed, 0.07f, s1),
            surfaceContainer = mix(seed, if (trueBlack) 0.09f else 0.10f, s2),
            surfaceContainerHigh = mix(seed, 0.12f, 0xFF232329.toInt()),
            surfaceContainerHighest = mix(seed, 0.14f, 0xFF2A2A31.toInt()),
            onSurface = 0xFFF4F3F8.toInt(),
            onSurfaceVariant = 0xFF9C99A8.toInt(),
            outline = mix(seed, 0.12f, 0xFF34343C.toInt()),
            outlineVariant = mix(seed, 0.08f, 0xFF24242A.toInt()),
            hairline = withAlpha(WHITE, 0.07f),
            glow = withAlpha(seed, if (trueBlack) 0.28f else 0.40f),
            navContainer = withAlpha(mix(seed, 0.06f, if (trueBlack) 0xFF121216.toInt() else 0xFF1E1E24.toInt()), 0.94f),
            shadow = withAlpha(BLACK, 0.80f),
        )
    }
}

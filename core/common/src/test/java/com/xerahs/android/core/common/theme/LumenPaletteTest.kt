package com.xerahs.android.core.common.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LumenPaletteTest {
    private val WHITE = 0xFFFFFFFF.toInt()
    private val BLACK = 0xFF000000.toInt()
    private val presets = listOf(
        0xFFB8F23A, 0xFF2BE0E0, 0xFF6D3BF0, 0xFF3B82F6, 0xFFFFB020, 0xFFFF5C8A, 0xFF34D17A,
        0xFF808080, 0xFFFFFF00, 0xFF0000FF
    ).map { it.toInt() }

    private fun assertClose(expected: Int, actual: Int) {
        for (shift in listOf(16, 8, 0)) {
            val e = (expected ushr shift) and 0xFF
            val a = (actual ushr shift) and 0xFF
            assertTrue("channel $shift: $e vs $a", kotlin.math.abs(e - a) <= 1)
        }
    }

    @Test fun mixZeroIsBase() = assertClose(WHITE, LumenPalette.mix(0xFFFF0000.toInt(), 0f, WHITE))
    @Test fun mixOneIsColor() = assertClose(0xFF6D3BF0.toInt(), LumenPalette.mix(0xFF6D3BF0.toInt(), 1f, WHITE))
    @Test fun mixHalfGreyIsBetween() {
        val m = LumenPalette.mix(BLACK, 0.5f, WHITE)
        val r = (m ushr 16) and 0xFF
        assertTrue(r in 90..140)
    }

    @Test fun withAlphaKeepsRgb() {
        val c = LumenPalette.withAlpha(0xFF123456.toInt(), 0.5f)
        assertEquals(0x123456, c and 0xFFFFFF)
        assertEquals(0x80, (c ushr 24) and 0xFF)
    }

    @Test fun trueBlackBackgroundIsBlack() {
        presets.forEach { assertEquals(BLACK, LumenPalette.derive(it, LumenMode.TRUE_BLACK).background) }
    }

    @Test fun lightSurfaceIsWhite() {
        presets.forEach { assertEquals(WHITE, LumenPalette.derive(it, LumenMode.LIGHT).surface) }
    }

    @Test fun textContrastHoldsEverywhere() {
        for (seed in presets) for (mode in LumenMode.values()) {
            val r = LumenPalette.derive(seed, mode)
            val tag = "seed=${Integer.toHexString(seed)} mode=$mode"
            assertTrue("$tag onSurface/bg", AccentDerivation.contrastRatio(r.onSurface, r.background) >= 4.5)
            assertTrue("$tag variant/surface", AccentDerivation.contrastRatio(r.onSurfaceVariant, r.surface) >= 4.5)
            assertTrue("$tag variant/bg", AccentDerivation.contrastRatio(r.onSurfaceVariant, r.background) >= 4.5)
            assertTrue("$tag onAccent/accent", AccentDerivation.contrastRatio(r.onAccent, r.accent) >= 4.5)
            assertTrue("$tag ink/surface", AccentDerivation.contrastRatio(r.ink, r.surface) >= 4.5)
            assertTrue("$tag ink/tint", AccentDerivation.contrastRatio(r.ink, r.tint) >= 4.5)
        }
    }

    @Test fun lightBackgroundIsTintedNotWhite() {
        val r = LumenPalette.derive(0xFF6D3BF0.toInt(), LumenMode.LIGHT)
        assertTrue(r.background != WHITE)
        assertTrue(AccentDerivation.relativeLuminance(r.background) > 0.85)
    }
}

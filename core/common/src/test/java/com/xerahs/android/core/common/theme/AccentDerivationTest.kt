package com.xerahs.android.core.common.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentDerivationTest {
    private val WHITE = 0xFFFFFFFF.toInt()
    private val BLACK = 0xFF000000.toInt()
    private val LIME = 0xFFB8F23A.toInt()
    private val DEEP_BLUE = 0xFF00008B.toInt()

    @Test fun contrastBlackWhiteIsMax() {
        assertEquals(21.0, AccentDerivation.contrastRatio(BLACK, WHITE), 0.1)
    }

    @Test fun onColorIsBlackForLightAccent() {
        assertEquals(BLACK, AccentDerivation.onColorFor(LIME))
    }

    @Test fun onColorIsWhiteForDarkAccent() {
        assertEquals(WHITE, AccentDerivation.onColorFor(DEEP_BLUE))
    }

    @Test fun lightModeAccentTextIsAaAgainstWhite() {
        for (seed in listOf(LIME, 0xFF2BE0E0.toInt(), 0xFFFFD000.toInt())) {
            val roles = AccentDerivation.deriveAccent(seed, dark = false)
            assertTrue(
                "accentText must be >= 4.5:1 on white",
                AccentDerivation.contrastRatio(roles.accentText, WHITE) >= 4.5
            )
        }
    }

    @Test fun darkModeUsesBrightSeedDirectly() {
        val roles = AccentDerivation.deriveAccent(LIME, dark = true)
        assertEquals(LIME, roles.accent)
        assertEquals(BLACK, roles.onAccent)
    }
}

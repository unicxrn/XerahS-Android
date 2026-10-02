package com.xerahs.android.core.common.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMathTest {
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    @Test fun defaultSettingsAreIdentity() {
        assertTrue(EffectSettings().isIdentity)
        assertArrayEquals(ColorMatrices.identity(), ColorMatrices.forSettings(EffectSettings()), 0.0001f)
    }

    @Test fun invertFlipsWhiteToBlack() {
        val out = ColorMatrices.applyToPixel(ColorMatrices.forSettings(EffectSettings(invert = true)), 0xFFFFFFFF.toInt())
        assertEquals(0, r(out)); assertEquals(0, g(out)); assertEquals(0, b(out))
    }

    @Test fun grayscaleEqualisesChannels() {
        val out = ColorMatrices.applyToPixel(ColorMatrices.forSettings(EffectSettings(grayscale = true)), 0xFFFF0000.toInt())
        assertEquals(r(out), g(out)); assertEquals(g(out), b(out))
        assertEquals(54, r(out))
    }

    @Test fun brightnessClampsAt255() {
        val out = ColorMatrices.applyToPixel(ColorMatrices.forSettings(EffectSettings(brightness = 1f)), 0xFF808080.toInt())
        assertEquals(255, r(out))
    }

    @Test fun watermarkCorners() {
        assertEquals(10f to 10f, WatermarkLayout.position(1000, 500, 200f, 40f, Corner.TOP_LEFT, 10f))
        assertEquals(790f to 450f, WatermarkLayout.position(1000, 500, 200f, 40f, Corner.BOTTOM_RIGHT, 10f))
    }

    @Test fun edgeColorAveragesBorderOnly() {
        val red = 0xFFFF0000.toInt(); val blue = 0xFF0000FF.toInt()
        val pixels = IntArray(9) { red }.also { it[4] = blue }
        assertEquals(red, EdgeColor.average(pixels, 3, 3))
    }
}

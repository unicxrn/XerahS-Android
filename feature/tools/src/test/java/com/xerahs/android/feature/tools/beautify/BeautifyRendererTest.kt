package com.xerahs.android.feature.tools.beautify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BeautifyRendererTest {

    @Test
    fun autoAddsPaddingOfLongEdgeOnEachSide() {
        assertEquals(1160 to 660, outputSize(1000, 500, BeautifyOptions(paddingPct = 0.08f, aspect = Aspect.AUTO)))
    }

    @Test
    fun squareSideCoversPaddedLongEdge() {
        val (w, h) = outputSize(1000, 500, BeautifyOptions(aspect = Aspect.SQUARE))
        assertEquals(w, h)
        assertTrue(w >= 1160)
        val (tw, th) = outputSize(300, 900, BeautifyOptions(aspect = Aspect.SQUARE))
        assertEquals(tw, th)
        assertTrue(th >= 900 + 2 * 72)
    }

    @Test
    fun wideIsSixteenByNine() {
        listOf(1000 to 500, 500 to 1000, 1920 to 1080, 333 to 777).forEach { (sw, sh) ->
            val (w, h) = outputSize(sw, sh, BeautifyOptions(aspect = Aspect.WIDE_16_9))
            assertTrue("$w x $h", abs(w - h * 16f / 9f) <= 1f)
            val pad = Math.round(0.08f * maxOf(sw, sh))
            assertTrue(w >= sw + 2 * pad && h >= sh + 2 * pad)
        }
    }

    @Test
    fun portraitIsFourByFive() {
        val (w, h) = outputSize(1000, 500, BeautifyOptions(aspect = Aspect.PORTRAIT_4_5))
        assertTrue("$w x $h", abs(w - h * 4f / 5f) <= 1f)
        assertTrue(w >= 1160 && h >= 660)
    }

    @Test
    fun largeSourcesAreCappedKeepingRatio() {
        val (w, h) = outputSize(12000, 9000, BeautifyOptions(aspect = Aspect.AUTO), maxEdge = 4096)
        assertEquals(4096, maxOf(w, h))
        val uncapped = outputSize(12000, 9000, BeautifyOptions(aspect = Aspect.AUTO), maxEdge = Int.MAX_VALUE)
        assertTrue(abs(w.toFloat() / h - uncapped.first.toFloat() / uncapped.second) < 0.002f)

        val (ww, wh) = outputSize(20000, 3000, BeautifyOptions(aspect = Aspect.WIDE_16_9), maxEdge = 4096)
        assertEquals(4096, ww)
        assertTrue(abs(ww - wh * 16f / 9f) <= 1f)
    }

    @Test
    fun zeroPaddingAutoKeepsSourceSize() {
        assertEquals(640 to 480, outputSize(640, 480, BeautifyOptions(paddingPct = 0f)))
    }
}

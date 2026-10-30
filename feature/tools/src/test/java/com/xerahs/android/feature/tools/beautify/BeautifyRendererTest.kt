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

    @Test
    fun placementCentresImageInsidePadding() {
        val o = BeautifyOptions(paddingPct = 0.08f, cornerRadiusPct = 0.03f)
        val p = placement(1000, 500, o, 1160, 660)
        assertEquals(80f, p.left, 0.01f)
        assertEquals(80f, p.top, 0.01f)
        assertEquals(1080f, p.right, 0.01f)
        assertEquals(580f, p.bottom, 0.01f)
        assertEquals(30f, p.radius, 0.01f)
    }

    @Test
    fun placementCentresInsideWiderAspect() {
        val o = BeautifyOptions(aspect = Aspect.WIDE_16_9)
        val (w, h) = outputSize(500, 1000, o)
        val p = placement(500, 1000, o, w, h)
        assertEquals((w - 500) / 2f, p.left, 0.5f)
        assertEquals((h - 1000) / 2f, p.top, 0.5f)
        assertEquals(500f, p.right - p.left, 0.5f)
    }

    @Test
    fun placementScalesWithCappedCanvas() {
        val o = BeautifyOptions()
        val (w, h) = outputSize(10000, 5000, o, maxEdge = 4096)
        val p = placement(10000, 5000, o, w, h)
        val s = w / 11600f
        assertEquals(10000 * s, p.right - p.left, 1f)
        assertEquals(800 * s, p.left, 1f)
    }

    @Test
    fun radiusNeverPassesHalfTheShortSide() {
        val p = placement(1000, 20, BeautifyOptions(cornerRadiusPct = 0.12f), 1160, 180)
        assertEquals(10f, p.radius, 0.01f)
    }

    @Test
    fun shadowBlurIsThreePercentWhenThereIsRoom() {
        val p = placement(1000, 500, BeautifyOptions(), 1160, 660)
        assertEquals(30f, shadowBlur(p, 1160, 660), 0.01f)
    }

    @Test
    fun shadowBlurAndOffsetFitInsideTheNarrowestMargin() {
        // 1% padding: a 10 px margin, so the blur shrinks to 10 / 1.4.
        val o = BeautifyOptions(paddingPct = 0.01f)
        val (w, h) = outputSize(1000, 500, o)
        val p = placement(1000, 500, o, w, h)
        val blur = shadowBlur(p, w, h)
        assertEquals(10f / 1.4f, blur, 0.01f)
        assertTrue(blur * (1f + SHADOW_OFFSET) <= h - p.bottom + 0.01f)
    }

    @Test
    fun noShadowWithoutPadding() {
        val o = BeautifyOptions(paddingPct = 0f)
        val p = placement(1000, 500, o, 1000, 500)
        assertEquals(0f, shadowBlur(p, 1000, 500), 0f)
    }

    @Test
    fun backgroundCodecRoundTrips() {
        val all = (0..5).map { Background.Gradient(it) } +
            listOf(Background.Solid(0xFFFFFFFF.toInt()), Background.Solid(0xFF000000.toInt()), Background.Solid(0x00000000), Background.Accent, Background.Transparent)
        all.forEach { assertEquals(it, BackgroundCodec.decode(BackgroundCodec.encode(it))) }
        assertEquals(Background.Gradient(0), BackgroundCodec.decode("garbage"))
    }
}

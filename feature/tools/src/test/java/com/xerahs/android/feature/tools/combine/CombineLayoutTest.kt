package com.xerahs.android.feature.tools.combine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombineLayoutTest {

    @Test
    fun verticalMatchSizeScalesEveryImageToWidestWidth() {
        val r = layout(listOf(100 to 50, 200 to 100), CombineOptions(vertical = true, matchSize = true))
        assertEquals(200, r.width)
        assertEquals(200, r.height)
        assertEquals(Rect4(0, 0, 200, 100), r.rects[0])
        assertEquals(Rect4(0, 100, 200, 200), r.rects[1])
    }

    @Test
    fun horizontalCenterWithoutMatchSizeCentresShorterImage() {
        val r = layout(
            listOf(100 to 40, 50 to 100),
            CombineOptions(vertical = false, matchSize = false, align = Align.CENTER)
        )
        assertEquals(150, r.width)
        assertEquals(100, r.height)
        assertEquals(Rect4(0, 30, 100, 70), r.rects[0])
        assertEquals(Rect4(100, 0, 150, 100), r.rects[1])
    }

    @Test
    fun horizontalStartAndEndAlignToEdges() {
        val sizes = listOf(100 to 40, 50 to 100)
        val start = layout(sizes, CombineOptions(vertical = false, matchSize = false, align = Align.START))
        assertEquals(0, start.rects[0].top)
        val end = layout(sizes, CombineOptions(vertical = false, matchSize = false, align = Align.END))
        assertEquals(60, end.rects[0].top)
        assertEquals(100, end.rects[0].bottom)
    }

    @Test
    fun verticalWithoutMatchSizeAlignsHorizontally() {
        val r = layout(listOf(100 to 10, 40 to 10), CombineOptions(vertical = true, matchSize = false, align = Align.END))
        assertEquals(100, r.width)
        assertEquals(Rect4(60, 10, 100, 20), r.rects[1])
    }

    @Test
    fun spacingGoesBetweenImagesNotAtEdges() {
        val r = layout(
            listOf(100 to 50, 100 to 50, 100 to 50),
            CombineOptions(vertical = true, matchSize = true, spacingPx = 10)
        )
        assertEquals(100, r.width)
        assertEquals(50 * 3 + 10 * 2, r.height)
        assertEquals(0, r.rects[0].top)
        assertEquals(60, r.rects[1].top)
        assertEquals(120, r.rects[2].top)
        assertEquals(r.height, r.rects[2].bottom)
    }

    @Test
    fun horizontalMatchSizeScalesToTallestHeight() {
        val r = layout(listOf(100 to 50, 100 to 100), CombineOptions(vertical = false, matchSize = true))
        assertEquals(300, r.width)
        assertEquals(100, r.height)
        assertEquals(Rect4(0, 0, 200, 100), r.rects[0])
    }

    @Test
    fun resultOverMaxEdgeScalesEveryRectProportionally() {
        val r = layout(
            listOf(4000 to 3000, 4000 to 3000, 4000 to 3000, 4000 to 3000),
            CombineOptions(vertical = true, matchSize = true, spacingPx = 100),
        )
        // Unscaled: 4000 x (12000 + 300) = 12300 tall, so scale is 8000 / 12300.
        val s = 8000.0 / 12300.0
        assertTrue(r.height <= 8000)
        assertTrue(r.height >= 8000 - 8)
        assertEquals((4000 * s).toInt(), r.width)
        r.rects.forEach {
            assertEquals((4000 * s).toInt(), it.width)
            assertEquals((3000 * s).toInt(), it.height)
            assertTrue(it.right <= r.width)
            assertTrue(it.bottom <= r.height)
        }
        assertEquals(r.height, r.rects.last().bottom)
    }

    @Test
    fun scaledRectsTouchWithoutSpacing() {
        val r = layout(List(7) { 3001 to 1999 }, CombineOptions(vertical = true, matchSize = true), maxEdge = 5000)
        r.rects.zipWithNext().forEach { (a, b) -> assertEquals(a.bottom, b.top) }
        assertEquals(0, r.rects.first().top)
        assertEquals(r.height, r.rects.last().bottom)
    }

    @Test
    fun scaledRectsAreEvenlySpaced() {
        val r = layout(List(9) { 1777 to 1333 }, CombineOptions(vertical = false, matchSize = true, spacingPx = 37), maxEdge = 4000)
        val gaps = r.rects.zipWithNext().map { (a, b) -> b.left - a.right }.toSet()
        assertEquals(1, gaps.size)
        assertTrue(gaps.single() in 1 until 37)
    }

    @Test
    fun hugeTotalsDoNotOverflow() {
        // 20 x 200000 px tall is 4e6, fine; matchSize to a 100000 px wide image pushes the sum past Int.MAX_VALUE.
        val sizes = listOf(100_000 to 10) + List(19) { 10 to 1_000_000 }
        val r = layout(sizes, CombineOptions(vertical = true, matchSize = true))
        assertTrue(r.width in 1..8000)
        assertTrue(r.height in 1..8000)
        r.rects.forEach {
            assertTrue(it.top >= 0 && it.bottom <= r.height)
            assertTrue(it.left >= 0 && it.right <= r.width)
        }
        r.rects.zipWithNext().forEach { (a, b) -> assertTrue(b.top >= a.bottom) }
    }

    @Test
    fun maxPixelsScalesAreaDown() {
        // 8000 x 8000 = 64 MP, capped at 16 MP: scale is sqrt(16/64) = 0.5.
        val r = layout(listOf(4000 to 8000, 4000 to 8000), CombineOptions(vertical = false), maxPixels = 16_000_000L)
        assertEquals(4000, r.width)
        assertEquals(4000, r.height)
        assertEquals(Rect4(2000, 0, 4000, 4000), r.rects[1])
        assertTrue(r.width.toLong() * r.height <= 16_000_000L)
    }

    @Test
    fun maxPixelsLeavesSmallResultsAlone() {
        val r = layout(listOf(100 to 50, 100 to 50), CombineOptions(), maxPixels = 1_000_000L)
        assertEquals(100, r.width)
        assertEquals(100, r.height)
    }

    @Test
    fun customMaxEdgeIsHonoured() {
        val r = layout(listOf(2000 to 1000, 2000 to 1000), CombineOptions(vertical = false), maxEdge = 1000)
        assertEquals(1000, r.width)
        assertEquals(250, r.height)
        assertEquals(Rect4(500, 0, 1000, 250), r.rects[1])
    }

    @Test
    fun emptyInputGivesEmptyResult() {
        val r = layout(emptyList(), CombineOptions())
        assertEquals(0, r.width)
        assertEquals(0, r.height)
        assertTrue(r.rects.isEmpty())
    }

    @Test
    fun sampleKeepsDecodeAtLeastTargetSize() {
        // 8000x6000 into 1000x750: 4 gives 2000x1500, 8 gives exactly 1000x750.
        assertEquals(8, sampleSizeFor(8000, 6000, 1000, 750, Long.MAX_VALUE))
        assertEquals(4, sampleSizeFor(8000, 6000, 1001, 750, Long.MAX_VALUE))
        assertEquals(1, sampleSizeFor(800, 600, 800, 600, Long.MAX_VALUE))
    }

    @Test
    fun sampleRespectsPixelBudget() {
        // 100 MP photo with a 64 MP / 20 budget per image.
        val budget = MAX_INPUT_PIXELS / 20
        val s = sampleSizeFor(12000, 8400, 8000, 5600, budget)
        assertTrue((12000L / s) * (8400L / s) <= budget)
        assertEquals(8, s)
    }

    @Test
    fun totalSampledPixelsStayUnderBudgetForTwentyHugeImages() {
        val budget = MAX_INPUT_PIXELS / 20
        val total = (1..20).sumOf {
            val s = sampleSizeFor(12000, 8400, 8000, 5600, budget)
            (12000L / s) * (8400L / s)
        }
        assertTrue(total <= MAX_INPUT_PIXELS)
    }
}

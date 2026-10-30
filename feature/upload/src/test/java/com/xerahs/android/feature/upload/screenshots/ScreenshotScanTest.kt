package com.xerahs.android.feature.upload.screenshots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotScanTest {
    @Test fun lookbackIsInclusiveAndNeverNegative() {
        assertEquals(940L, ScreenshotScan.queryFrom(1000))
        assertEquals(0L, ScreenshotScan.queryFrom(10))
    }

    @Test fun seenRoundTrips() {
        assertEquals(listOf(3L, 5L), ScreenshotScan.parseSeen(ScreenshotScan.encodeSeen(listOf(3, 5))))
        assertEquals(emptyList<Long>(), ScreenshotScan.parseSeen(""))
        assertEquals(listOf(7L), ScreenshotScan.parseSeen("x,7,,"))
    }

    @Test fun mergeKeepsNewestAndDedupes() {
        val merged = ScreenshotScan.mergeSeen((1L..200L).toList(), listOf(200L, 201L, 202L))
        assertEquals(200, merged.size)
        assertEquals(3L, merged.first())
        assertEquals(202L, merged.last())
    }

    @Test fun seenIdsAreSkipped() {
        assertFalse(ScreenshotScan.isNew(5, setOf(5L)))
        assertTrue(ScreenshotScan.isNew(6, setOf(5L)))
    }

    @Test fun nextCheckOnlyAdvancesWithHandledItems() {
        assertEquals(100L, ScreenshotScan.nextCheck(100, emptyList()))
        assertEquals(150L, ScreenshotScan.nextCheck(100, listOf(120, 150)))
        assertEquals(100L, ScreenshotScan.nextCheck(100, listOf(90)))
    }
}

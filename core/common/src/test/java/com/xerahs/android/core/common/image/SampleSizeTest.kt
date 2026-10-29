package com.xerahs.android.core.common.image

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleSizeTest {
    @Test fun smallImageIsNotSampled() {
        assertEquals(1, calculateInSampleSize(4000, 3000, 4096))
        assertEquals(1, calculateInSampleSize(4096, 4096, 4096))
    }

    @Test fun hundredMegapixelPhotoFitsTheCap() {
        // 12240 x 8160 (100 MP): /2 = 6120, /4 = 3060
        assertEquals(4, calculateInSampleSize(12240, 8160, 4096))
    }

    @Test fun usesTheLongEdgeInEitherOrientation() {
        assertEquals(2, calculateInSampleSize(3000, 8000, 4096))
        assertEquals(2, calculateInSampleSize(8000, 3000, 4096))
    }

    @Test fun unknownOrInvalidSizeIsNotSampled() {
        assertEquals(1, calculateInSampleSize(-1, -1, 4096))
        assertEquals(1, calculateInSampleSize(9000, 9000, 0))
    }
}

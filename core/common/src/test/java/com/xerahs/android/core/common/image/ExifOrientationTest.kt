package com.xerahs.android.core.common.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifOrientationTest {
    @Test fun mapsAllEightOrientations() {
        assertTrue(orientationOp(1).isIdentity)
        assertEquals(OrientationOp(0, true), orientationOp(2))
        assertEquals(OrientationOp(180, false), orientationOp(3))
        assertEquals(OrientationOp(180, true), orientationOp(4))
        assertEquals(OrientationOp(90, true), orientationOp(5))
        assertEquals(OrientationOp(90, false), orientationOp(6))
        assertEquals(OrientationOp(270, true), orientationOp(7))
        assertEquals(OrientationOp(270, false), orientationOp(8))
    }

    @Test fun unknownIsIdentity() {
        assertTrue(orientationOp(0).isIdentity)
        assertTrue(orientationOp(99).isIdentity)
    }
}

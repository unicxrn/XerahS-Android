package com.xerahs.android.feature.annotation.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapTransformsTest {

    // A 2x1 image: pixel A at (0,0), pixel B at (1,0).
    private val w = 2
    private val h = 1

    @Test
    fun `rotate right maps the top left corner to the top right of the new width`() {
        assertEquals(h - 1 to 0, mapPoint(0, 0, w, h, Op.ROTATE_RIGHT))
        assertEquals(0 to 1, mapPoint(1, 0, w, h, Op.ROTATE_RIGHT))
    }

    @Test
    fun `rotate left maps the top left corner to the bottom left`() {
        assertEquals(0 to w - 1, mapPoint(0, 0, w, h, Op.ROTATE_LEFT))
        assertEquals(0 to 0, mapPoint(1, 0, w, h, Op.ROTATE_LEFT))
    }

    @Test
    fun `rotate 180 swaps opposite corners`() {
        assertEquals(1 to 0, mapPoint(0, 0, w, h, Op.ROTATE_180))
        assertEquals(0 to 0, mapPoint(1, 0, w, h, Op.ROTATE_180))
    }

    @Test
    fun `flip horizontally mirrors x`() {
        assertEquals(w - 1 to 0, mapPoint(0, 0, w, h, Op.FLIP_HORIZONTAL))
        assertEquals(0 to 0, mapPoint(1, 0, w, h, Op.FLIP_HORIZONTAL))
    }

    @Test
    fun `flip vertically mirrors y`() {
        assertEquals(1 to 2, mapPoint(1, 0, 3, 3, Op.FLIP_VERTICAL))
        assertEquals(0 to 0, mapPoint(0, 0, w, h, Op.FLIP_VERTICAL))
    }

    @Test
    fun `rotating left undoes rotating right`() {
        for (x in 0 until 3) for (y in 0 until 2) {
            val (rx, ry) = mapPoint(x, y, 3, 2, Op.ROTATE_RIGHT)
            assertEquals(x to y, mapPoint(rx, ry, 2, 3, Op.ROTATE_LEFT))
        }
    }

    @Test
    fun `degrees match each rotation`() {
        assertEquals(90, Op.ROTATE_RIGHT.degrees)
        assertEquals(-90, Op.ROTATE_LEFT.degrees)
        assertEquals(180, Op.ROTATE_180.degrees)
    }
}

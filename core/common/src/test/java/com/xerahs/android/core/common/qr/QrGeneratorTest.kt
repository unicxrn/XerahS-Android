package com.xerahs.android.core.common.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrGeneratorTest {
    @Test fun generatesNonTrivialSquareMatrix() {
        val m = QrGenerator.generate("https://i.xerahs.io/a8F3k9.png")
        assertTrue("size must be > 0", m.size > 0)
        assertEquals("cells must be size*size", m.size * m.size, m.cells.size)
        assertTrue("must contain dark cells", m.cells.any { it })
        assertTrue("must contain light cells", m.cells.any { !it })
    }

    @Test fun finderPatternIsDark() {
        // With a 1-module quiet zone (needed for scannability), the top-left finder
        // pattern's dark corner sits at (1,1), not (0,0) which is quiet-zone padding.
        val m = QrGenerator.generate("hello")
        assertTrue("QR finder pattern corner should be dark", m.isDark(1, 1))
    }

    @Test fun blankTextStillProducesAMatrix() {
        val m = QrGenerator.generate(" ")
        assertTrue(m.size > 0)
    }
}

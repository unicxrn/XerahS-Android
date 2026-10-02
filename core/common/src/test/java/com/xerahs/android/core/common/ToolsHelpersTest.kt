package com.xerahs.android.core.common

import com.xerahs.android.core.common.image.BatchSizing
import com.xerahs.android.core.common.image.ColorFormat
import com.xerahs.android.core.common.image.FitMapping
import com.xerahs.android.core.common.qr.QrDecoder
import com.xerahs.android.core.common.qr.QrGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsHelpersTest {
    @Test fun hashesOfEmptyInput() {
        val h = FileHasher.computeAll(ByteArray(0).inputStream())
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", h.md5)
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", h.sha1)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", h.sha256)
    }

    @Test fun hashesOfAbc() {
        val h = FileHasher.computeAll("abc".byteInputStream())
        assertEquals("900150983cd24fb0d6963f7d28e17f72", h.md5)
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", h.sha1)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h.sha256)
    }

    @Test fun hashMatchIgnoresCaseAndSpaces() {
        assertTrue(FileHasher.matches(" 900150983CD24FB0D6963F7D28E17F72 ", "900150983cd24fb0d6963f7d28e17f72"))
        assertFalse(FileHasher.matches("abc", "900150983cd24fb0d6963f7d28e17f72"))
    }

    @Test fun qrRoundTrip() {
        val m = QrGenerator.generate("https://xerahs.test/a")
        val scale = 8; val quiet = 4
        val size = (m.size + 2 * quiet) * scale
        val pixels = IntArray(size * size) { i ->
            val x = (i % size) / scale - quiet; val y = (i / size) / scale - quiet
            if (x in 0 until m.size && y in 0 until m.size && m.isDark(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        assertEquals("https://xerahs.test/a", QrDecoder.decode(pixels, size, size))
    }

    @Test fun qrDecodeOfBlankImageIsNull() = assertNull(QrDecoder.decode(IntArray(100) { -1 }, 10, 10))

    @Test fun colorFormats() {
        assertEquals("#FF8000", ColorFormat.hex(0xFFFF8000.toInt()))
        assertEquals("rgb(255, 128, 0)", ColorFormat.rgb(0xFFFF8000.toInt()))
        assertEquals(0xFFFF8000.toInt(), ColorFormat.parseHex("#ff8000"))
    }

    @Test fun fitMappingCentersAndRejectsLetterbox() {
        // 200x100 bitmap in a 400x400 view: scale 2, drawn 400x200 at y offset 100.
        assertEquals(100 to 50, FitMapping.toBitmap(200f, 200f, 400, 400, 200, 100))
        assertNull(FitMapping.toBitmap(200f, 50f, 400, 400, 200, 100))
    }

    @Test fun batchSizingKeepsAspect() {
        assertEquals(1920 to 1080, BatchSizing.target(3840, 2160, 1920))
        assertEquals(800 to 600, BatchSizing.target(800, 600, 1920))
        assertEquals(3840 to 2160, BatchSizing.target(3840, 2160, 0))
    }
}

package com.xerahs.android.core.common.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTypesTest {
    @Test fun mapsKnownExtensionsCaseInsensitively() {
        assertEquals("image/png", MimeTypes.fromFileName("Shot.PNG"))
        assertEquals("image/jpeg", MimeTypes.fromFileName("a.jpeg"))
        assertEquals("application/pdf", MimeTypes.fromFileName("doc.pdf"))
        assertEquals("text/plain", MimeTypes.fromFileName("notes.txt"))
    }

    @Test fun unknownOrMissingExtensionIsOctetStream() {
        assertEquals(MimeTypes.OCTET_STREAM, MimeTypes.fromFileName("archive.xyz"))
        assertEquals(MimeTypes.OCTET_STREAM, MimeTypes.fromFileName("README"))
    }

    @Test fun extensionForPrefersCanonicalExtension() {
        assertEquals("jpg", MimeTypes.extensionFor("image/jpeg"))
        assertEquals("txt", MimeTypes.extensionFor("text/plain"))
        assertEquals(null, MimeTypes.extensionFor("application/x-unknown"))
    }

    @Test fun classifiesImagesAndText() {
        assertTrue(MimeTypes.isRasterImage("image/png"))
        assertTrue(MimeTypes.isRasterImage("image/*"))
        assertFalse(MimeTypes.isRasterImage("image/svg+xml"))
        assertFalse(MimeTypes.isRasterImage("video/mp4"))
        assertTrue(MimeTypes.isHeic("image/heic"))
        assertTrue(MimeTypes.isText("application/json"))
        assertTrue(MimeTypes.isText("text/markdown"))
        assertFalse(MimeTypes.isText("application/pdf"))
    }

    @Test fun gifsAndHeicSkipTheEditor() {
        assertTrue(MimeTypes.opensInEditor("image/png"))
        assertTrue(MimeTypes.opensInEditor("image/jpeg"))
        assertFalse(MimeTypes.opensInEditor("image/gif"))
        assertFalse(MimeTypes.opensInEditor("image/heic"))
        assertFalse(MimeTypes.opensInEditor("video/mp4"))
        assertFalse(MimeTypes.opensInEditor(null))
    }

    @Test fun detectsAnimatedWebp() {
        fun header(flags: Int) = ByteArray(32).also {
            "RIFF".toByteArray().copyInto(it, 0); "WEBP".toByteArray().copyInto(it, 8)
            "VP8X".toByteArray().copyInto(it, 12); it[20] = flags.toByte()
        }
        assertTrue(EditorRouting.isAnimatedWebp(header(0x02)))
        assertFalse(EditorRouting.isAnimatedWebp(header(0x00)))
        assertFalse(EditorRouting.isAnimatedWebp(ByteArray(4)))
    }
}

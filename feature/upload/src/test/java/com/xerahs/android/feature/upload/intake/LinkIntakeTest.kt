package com.xerahs.android.feature.upload.intake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkIntakeTest {

    // singleUrlOrNull

    @Test fun textAroundUrlIsNotSingleUrl() = assertNull(singleUrlOrNull("see https://a.b"))
    @Test fun trimmedUrl() = assertEquals("https://a.b/x", singleUrlOrNull(" https://a.b/x "))
    @Test fun httpAllowed() = assertEquals("http://a.b", singleUrlOrNull("http://a.b"))
    @Test fun schemeCaseInsensitive() = assertEquals("HTTPS://a.b/x", singleUrlOrNull("HTTPS://a.b/x"))
    @Test fun ftpRejected() = assertNull(singleUrlOrNull("ftp://a.b/x"))
    @Test fun fileRejected() = assertNull(singleUrlOrNull("file:///sdcard/x.png"))
    @Test fun innerNewlineRejected() = assertNull(singleUrlOrNull("https://a.b\nhttps://c.d"))
    @Test fun noHostRejected() = assertNull(singleUrlOrNull("https://"))
    @Test fun blankRejected() = assertNull(singleUrlOrNull("   "))

    // resolveFileName

    @Test fun rfc5987FilenameWins() = assertEquals(
        "résumé.pdf",
        resolveFileName(
            "https://a.b/download?id=1",
            "attachment; filename=\"fallback.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf",
            "application/pdf"
        )
    )

    @Test fun plainFilename() = assertEquals(
        "report.pdf",
        resolveFileName("https://a.b/x", "attachment; filename=\"report.pdf\"", "application/pdf")
    )

    @Test fun unquotedFilename() = assertEquals(
        "report.pdf",
        resolveFileName("https://a.b/x", "attachment; filename=report.pdf", null)
    )

    @Test fun urlLastSegment() = assertEquals(
        "cat.jpg",
        resolveFileName("https://a.b/images/cat.jpg?w=200#top", null, "image/jpeg")
    )

    @Test fun urlSegmentIsDecoded() = assertEquals(
        "my cat.png",
        resolveFileName("https://a.b/my%20cat.png", null, "image/png")
    )

    @Test fun extensionAddedFromContentType() = assertEquals(
        "photo.png",
        resolveFileName("https://a.b/photo", null, "image/png")
    )

    @Test fun contentTypeParametersIgnored() = assertEquals(
        "notes.txt",
        resolveFileName("https://a.b/notes", null, "text/plain; charset=utf-8")
    )

    @Test fun noPathFallsBackToDownload() = assertEquals(
        "download.png",
        resolveFileName("https://a.b/", null, "image/png")
    )

    @Test fun slashesAndDotDotSanitized() {
        val name = resolveFileName("https://a.b/x", "attachment; filename=\"../../etc/passwd.txt\"", "text/plain")
        assertFalse(name.contains('/'))
        assertFalse(name.contains(".."))
        assertEquals("passwd.txt", name)
    }

    @Test fun encodedTraversalSanitized() {
        val name = resolveFileName("https://a.b/..%2F..%2Fsecret.png", null, "image/png")
        assertFalse(name.contains('/'))
        assertFalse(name.startsWith("."))
        assertTrue(name.endsWith(".png"))
    }

    @Test fun dotDotOnlyFallsBack() = assertEquals(
        "download.png",
        resolveFileName("https://a.b/x", "attachment; filename=\"..\"", "image/png")
    )

    @Test fun quotedBackslashPathSanitized() = assertEquals(
        "evil.png",
        resolveFileName("https://a.b/x", "attachment; filename=\"..\\\\..\\\\evil.png\"", "image/png")
    )

    @Test fun windowsPathSanitized() = assertEquals(
        "evil.png",
        resolveFileName("https://a.b/x", "attachment; filename=C:\\temp\\evil.png", "image/png")
    )

    @Test fun nulDropped() = assertEquals(
        "ab.png",
        resolveFileName("https://a.b/x", "attachment; filename=\"a\u0000b.png\"", "image/png")
    )

    @Test fun absolutePathKeepsOnlyName() = assertEquals(
        "x.png",
        resolveFileName("https://a.b/y", "attachment; filename=\"/x\"", "image/png")
    )

    // redirects

    @Test fun httpsToHttpIsInsecure() = assertTrue(isInsecureRedirect(originalHttps = true, finalHttps = false))
    @Test fun httpToHttpsIsFine() = assertFalse(isInsecureRedirect(originalHttps = false, finalHttps = true))
    @Test fun httpsToHttpsIsFine() = assertFalse(isInsecureRedirect(originalHttps = true, finalHttps = true))

    // size guard

    @Test fun underCapPasses() = checkDownloadSize(100, 100)

    @Test fun overCapThrowsWithMessage() {
        val e = runCatching { checkDownloadSize(200L * 1024 * 1024 + 1, 200L * 1024 * 1024) }.exceptionOrNull()
        assertTrue(e is FileTooLargeException)
        assertEquals("That file is larger than 200 MB", e!!.message)
    }
}

package com.xerahs.android.core.common.file

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class CopyToCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun source(name: String?, mime: String?, size: Long? = null, stream: () -> InputStream?) = object : CopySource {
        override fun displayName() = name
        override fun mimeType() = mime
        override fun size() = size
        override fun open() = stream()
    }

    @Test fun keepsJpegExtensionFromMime() {
        val f = copyToCacheFrom(source("IMG_1", "image/jpeg") { ByteArrayInputStream(byteArrayOf(1, 2)) }, tmp.newFolder())
        assertEquals("IMG_1.jpg", f.name)
        assertEquals(2, f.length())
    }

    @Test fun usesDisplayNameExtension() {
        val f = copyToCacheFrom(source("report.pdf", "application/octet-stream") { ByteArrayInputStream(byteArrayOf(1)) }, tmp.newFolder())
        assertEquals("report.pdf", f.name)
    }

    @Test fun fallsBackToBin() {
        val f = copyToCacheFrom(source("thing", null) { ByteArrayInputStream(byteArrayOf(1)) }, tmp.newFolder())
        assertEquals("thing.bin", f.name)
    }

    @Test fun closesStreamOnError() {
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int = throw IOException("boom")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("boom")
            override fun close() { closed = true }
        }
        val dir = tmp.newFolder()
        try {
            copyToCacheFrom(source("a.png", "image/png") { stream }, dir)
            org.junit.Assert.fail("expected IOException")
        } catch (_: IOException) {
        }
        assertTrue(closed)
        assertFalse(java.io.File(dir, "a.png").exists())
    }

    @Test fun intakeCopiesBeforeNavigating() {
        val events = mutableListOf<String>()
        val copier = IntakeCopier<String> { name -> events += "copy:$name"; tmp.newFile(name) }
        runBlocking { copier.intake(listOf("a.png", "b.png"), onReady = { files, _ -> events += "navigate:${files.size}" }) }
        assertEquals(listOf("copy:a.png", "copy:b.png", "navigate:2"), events)
    }

    @Test fun intakeFailsWhenNothingCopies() {
        var failed = false
        var ready = false
        runBlocking {
            IntakeCopier<String> { throw IOException() }.intake(listOf("x"), onReady = { _, _ -> ready = true }, onFailed = { failed = true })
        }
        assertTrue(failed); assertFalse(ready)
    }

    @Test fun dottedNameWithoutRealExtensionGetsMimeExtension() {
        assertEquals("Screenshot 2024.10.30.png", fileNameFor("Screenshot 2024.10.30", "image/png"))
    }

    @Test fun unknownMimeKeepsNamesRealExtension() {
        assertEquals("notes.xyz", fileNameFor("notes.xyz", "application/x-foo"))
        assertEquals("notes.xyz", fileNameFor("notes.xyz", null))
        assertEquals("notes.xyz", fileNameFor("notes.xyz", MimeTypes.OCTET_STREAM))
    }

    @Test fun androidFallbackIsUsedBeforeBin() {
        assertEquals("a.zzz", fileNameFor("a", "application/x-foo") { "zzz" })
        assertEquals("a.bin", fileNameFor("a", "application/x-foo"))
    }

    @Test fun newTypesAreRecognised() {
        assertEquals("p.avif", fileNameFor("p", "image/avif"))
        assertEquals("v.3gp", fileNameFor("v", "video/3gpp"))
        assertEquals("r.dng", fileNameFor("r", "image/x-adobe-dng"))
        assertEquals("d.doc", fileNameFor("d", "application/msword"))
    }

    @Test fun capsLongExtensionAndStripsControlChars() {
        assertEquals("a.verylongextensionname.png", fileNameFor("a.ver\u0001ylongextensionname", "image/png"))
        assertEquals("ab.txt", fileNameFor("a\u0000b.txt", "text/plain"))
    }

    @Test fun failsWhenSourceDoesNotFit() {
        val dir = tmp.newFolder()
        try {
            copyToCacheFrom(source("a.bin", null, size = 1000) { ByteArrayInputStream(ByteArray(1)) }, dir, availableBytes = 10)
            org.junit.Assert.fail("expected InsufficientStorageException")
        } catch (e: InsufficientStorageException) {
            assertEquals("Not enough storage to copy this file", e.message)
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun partialFailureStillReportsRemainingFiles() {
        var ready: Pair<Int, Int>? = null
        val copier = IntakeCopier<String> { if (it == "bad") throw IOException() else tmp.newFile(it) }
        runBlocking { copier.intake(listOf("a", "bad", "bad"), onReady = { f, n -> ready = f.size to n }) }
        assertEquals(1 to 2, ready)
    }

    @Test fun cancellationIsNotSwallowed() {
        val copier = IntakeCopier<String> { throw kotlinx.coroutines.CancellationException("x") }
        try {
            runBlocking { copier.intake(listOf("a"), onReady = { _, _ -> }) }
            org.junit.Assert.fail("expected cancellation")
        } catch (_: kotlinx.coroutines.CancellationException) {
        }
    }
}

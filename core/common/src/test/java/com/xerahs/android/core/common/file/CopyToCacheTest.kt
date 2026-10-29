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

    private fun source(name: String?, mime: String?, stream: () -> InputStream?) = object : CopySource {
        override fun displayName() = name
        override fun mimeType() = mime
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
        runBlocking { copier.intake(listOf("a.png", "b.png"), onReady = { events += "navigate:${it.size}" }) }
        assertEquals(listOf("copy:a.png", "copy:b.png", "navigate:2"), events)
    }

    @Test fun intakeFailsWhenNothingCopies() {
        var failed = false
        var ready = false
        runBlocking {
            IntakeCopier<String> { throw IOException() }.intake(listOf("x"), onReady = { ready = true }, onFailed = { failed = true })
        }
        assertTrue(failed); assertFalse(ready)
    }
}

package com.xerahs.android.core.common.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalUploadsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun keepsACopyUnderUploads() {
        val filesDir = tmp.newFolder("files")
        val source = tmp.newFile("upload_123.jpg").apply { writeText("pixels") }
        val kept = LocalUploads.keep(filesDir, source, "id1", "shot.jpg")
        assertEquals(File(filesDir, "uploads/id1_shot.jpg").canonicalPath, kept.canonicalPath)
        assertEquals("pixels", kept.readText())
        assertTrue(source.exists())
    }

    @Test fun nameCannotEscapeTheFolder() {
        val filesDir = tmp.newFolder("files")
        val source = tmp.newFile("a.png").apply { writeText("x") }
        val kept = LocalUploads.keep(filesDir, source, "id2", "../../evil.png")
        assertEquals(File(filesDir, "uploads").canonicalPath, kept.canonicalFile.parent)
    }

    @Test fun onlyKeptFilesCountAsKept() {
        val filesDir = tmp.newFolder("files")
        val source = tmp.newFile("a.png").apply { writeText("x") }
        val kept = LocalUploads.keep(filesDir, source, "id3", "a.png")
        assertTrue(LocalUploads.isKept(filesDir, kept.path))
        assertFalse(LocalUploads.isKept(filesDir, source.path))
        assertFalse(LocalUploads.isKept(filesDir, File(filesDir, "uploads/../captures/x.png").path))
        assertFalse(LocalUploads.isKept(filesDir, null))
    }
}

package com.xerahs.android.core.common.file

import java.io.File

/**
 * Files "uploaded" to the Local destination. They are kept under `filesDir/uploads/` so the
 * history link keeps working, and removed again when the history item is deleted.
 */
object LocalUploads {
    private const val DIR = "uploads"

    fun dir(filesDir: File) = File(filesDir, DIR)

    /** Copies [source] to `uploads/<id>_<name>` and returns the copy. */
    fun keep(filesDir: File, source: File, id: String, name: String): File {
        val dir = dir(filesDir).apply { mkdirs() }
        val safeName = File(name).name.ifBlank { source.name }
        return source.copyTo(File(dir, "${id}_$safeName"), overwrite = true)
    }

    /** True only for a file directly inside the uploads folder. */
    fun isKept(filesDir: File, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return File(path).canonicalFile.parentFile == dir(filesDir).canonicalFile
    }
}

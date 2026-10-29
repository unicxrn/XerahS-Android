package com.xerahs.android.feature.upload.worker

import java.io.File
import java.util.UUID

/**
 * Remembers which files of a batch already uploaded, so a retried work skips them instead of
 * uploading again (and tripping duplicate detection on its own earlier results).
 * One small file per work under [dir], one "path<US>url" line per finished file.
 */
class BatchProgressStore(private val dir: File) {

    fun completed(workId: UUID): Set<String> = entries(workId).mapTo(LinkedHashSet()) { it.first }

    fun urls(workId: UUID): List<String> = entries(workId).mapNotNull { it.second }

    fun markDone(workId: UUID, path: String, url: String? = null) {
        dir.mkdirs()
        fileFor(workId).appendText("$path$SEP${url.orEmpty()}\n")
    }

    fun clear(workId: UUID) {
        fileFor(workId).delete()
    }

    private fun entries(workId: UUID): List<Pair<String, String?>> {
        val file = fileFor(workId)
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotEmpty() }.map { line ->
            line.substringBefore(SEP) to line.substringAfter(SEP, "").ifEmpty { null }
        }
    }

    private fun fileFor(workId: UUID) = File(dir, workId.toString())

    companion object {
        private const val SEP = '\u001F'

        fun forCache(cacheDir: File) = BatchProgressStore(File(cacheDir, "upload-progress"))
    }
}

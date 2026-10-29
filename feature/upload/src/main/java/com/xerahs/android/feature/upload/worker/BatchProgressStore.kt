package com.xerahs.android.feature.upload.worker

import java.io.File
import java.util.UUID

/**
 * Remembers which files of a batch already uploaded, so a retried work skips them instead of
 * uploading again (and tripping duplicate detection on its own earlier results).
 * One small file per work under [dir], one "path<US>url<US>hash" line per finished file.
 * Each write replaces the file through a rename, and a half-written last line is ignored.
 */
class BatchProgressStore(private val dir: File) {

    private data class Entry(val path: String, val url: String?, val hash: String?)

    fun completed(workId: UUID): Set<String> = entries(workId).mapTo(LinkedHashSet()) { it.path }

    fun urls(workId: UUID): List<String> = entries(workId).mapNotNull { it.url }

    /** Content hashes this work already uploaded; they must not count as duplicates. */
    fun hashes(workId: UUID): Set<String> = entries(workId).mapNotNullTo(LinkedHashSet()) { it.hash }

    fun markDone(workId: UUID, path: String, url: String? = null, hash: String? = null) {
        dir.mkdirs()
        val lines = entries(workId).map { it.line() } + Entry(path, url, hash).line()
        val tmp = File(dir, "$workId.tmp")
        tmp.writeText(lines.joinToString("") { "$it\n" })
        if (!tmp.renameTo(fileFor(workId))) {
            fileFor(workId).delete()
            tmp.renameTo(fileFor(workId))
        }
    }

    fun clear(workId: UUID) {
        fileFor(workId).delete()
    }

    /** Deletes records (and stray temp files) not touched for [maxAgeMs]: works that never finished cleanly. */
    fun prune(now: Long = System.currentTimeMillis(), maxAgeMs: Long = MAX_AGE_MS) {
        dir.listFiles()?.filter { now - it.lastModified() > maxAgeMs }?.forEach { it.delete() }
    }

    private fun entries(workId: UUID): List<Entry> {
        val file = fileFor(workId)
        if (!file.exists()) return emptyList()
        val text = file.readText()
        // Only complete lines count; a crash mid-write could leave a partial last one.
        return text.substring(0, text.lastIndexOf('\n') + 1).split('\n').filter { it.isNotEmpty() }.map { line ->
            val parts = line.split(SEP)
            Entry(parts[0], parts.getOrNull(1)?.ifEmpty { null }, parts.getOrNull(2)?.ifEmpty { null })
        }
    }

    private fun Entry.line() = "$path$SEP${url.orEmpty()}$SEP${hash.orEmpty()}"

    private fun fileFor(workId: UUID) = File(dir, workId.toString())

    companion object {
        private const val SEP = '\u001F'
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        fun forCache(cacheDir: File) = BatchProgressStore(File(cacheDir, "upload-progress")).apply { prune() }
    }
}

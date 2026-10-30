package com.xerahs.android.feature.upload.worker

import androidx.work.Data
import java.io.File
import java.util.Properties
import java.util.UUID

/**
 * Keeps the retry payload of a work Android stopped (foreground service timeout). WorkManager may
 * drop a progress update made after the stop and clears progress once the work is cancelled, so
 * the queue reads this file instead. One "<workId>.stopped" file per work, next to
 * [BatchProgressStore]'s records, so its week-long prune covers these too.
 */
class StoppedUploadStore(private val dir: File) {

    /** Saves what a retry needs: the [remaining] spec (null when nothing is left), [message] and [finishedAt]. */
    fun save(workId: UUID, remaining: UploadSpec?, message: String, finishedAt: Long) {
        dir.mkdirs()
        val props = Properties()
        remaining?.let { spec ->
            UploadEnqueuer.inputData(spec).keyValueMap.forEach { (k, v) -> if (v != null) props.setProperty(k, v.toString()) }
        }
        props.setProperty(UploadWorker.KEY_ERROR_MESSAGE, message)
        props.setProperty(UploadWorker.KEY_FINISHED_AT, finishedAt.toString())
        val tmp = File(dir, "$workId$SUFFIX.tmp")
        tmp.outputStream().use { props.store(it, null) }
        if (!tmp.renameTo(fileFor(workId))) {
            fileFor(workId).delete()
            tmp.renameTo(fileFor(workId))
        }
    }

    /** The payload as the queue reads failed output: spec keys, error message and finish time. */
    fun read(workId: UUID): Data? = load(fileFor(workId))

    /** Every stopped work still on disk. */
    fun all(): Map<UUID, Data> = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }.orEmpty()
        .mapNotNull { f ->
            val id = runCatching { UUID.fromString(f.name.removeSuffix(SUFFIX)) }.getOrNull() ?: return@mapNotNull null
            load(f)?.let { id to it }
        }.toMap()

    fun clear(workId: UUID) {
        fileFor(workId).delete()
    }

    private fun load(file: File): Data? {
        if (!file.exists()) return null
        val props = runCatching { Properties().apply { file.inputStream().use { load(it) } } }.getOrNull() ?: return null
        val builder = Data.Builder()
        for (key in props.stringPropertyNames()) {
            val value = props.getProperty(key)
            if (key == UploadWorker.KEY_FINISHED_AT) value.toLongOrNull()?.let { builder.putLong(key, it) }
            else builder.putString(key, value)
        }
        return builder.build()
    }

    private fun fileFor(workId: UUID) = File(dir, "$workId$SUFFIX")

    companion object {
        private const val SUFFIX = ".stopped"

        fun forCache(cacheDir: File) = StoppedUploadStore(File(cacheDir, "upload-progress"))
    }
}

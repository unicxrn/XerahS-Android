package com.xerahs.android.feature.upload.queue

import androidx.work.WorkInfo
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.feature.upload.worker.UploadEnqueuer
import com.xerahs.android.feature.upload.worker.UploadSpec
import com.xerahs.android.feature.upload.worker.UploadWorker
import java.util.UUID

data class DuplicateInfo(val path: String, val url: String?, val fileName: String?, val timestamp: Long)

data class QueueItem(
    val id: UUID,
    val fileName: String,
    val fileCount: Int,
    val destination: UploadDestination?,
    /** 0..1 while running with known progress, otherwise null. */
    val fraction: Float?,
    val error: String?,
    val finishedAt: Long?,
    val retrySpec: UploadSpec?,
    val isRunning: Boolean = false,
    val isActive: Boolean = false,
    val enqueuedAt: Long = 0L,
    val duplicate: DuplicateInfo? = null,
) {
    /** Files a retry would still upload. */
    val remaining: Int get() = retrySpec?.paths?.size ?: 0

    /** Retry that skips the duplicate check, for a file the user confirmed. */
    fun uploadAnywaySpec(): UploadSpec? = retrySpec?.copy(skipDuplicateCheck = true)

    /** Retry without the duplicate file, or null when nothing else is left. */
    fun skipFileSpec(): UploadSpec? {
        val spec = retrySpec ?: return null
        val left = spec.paths.filterNot { it == duplicate?.path }
        return if (left.isEmpty()) null else spec.copy(paths = left)
    }
}

data class QueueSections(
    val running: List<QueueItem>,
    val waiting: List<QueueItem>,
    val failed: List<QueueItem>,
    val done: List<QueueItem>,
) {
    val isEmpty: Boolean get() = running.isEmpty() && waiting.isEmpty() && failed.isEmpty() && done.isEmpty()
    val hasFinished: Boolean get() = failed.isNotEmpty() || done.isNotEmpty()

    companion object {
        const val DONE_WINDOW_MS = 24L * 60 * 60 * 1000

        /** Sorts work into sections. [hidden] holds finished work the user cleared or already retried. */
        fun build(infos: List<WorkInfo>, now: Long, hidden: Set<UUID>): QueueSections {
            val running = mutableListOf<QueueItem>()
            val waiting = mutableListOf<QueueItem>()
            val failed = mutableListOf<QueueItem>()
            val done = mutableListOf<QueueItem>()
            for (info in infos) {
                when (info.state) {
                    WorkInfo.State.RUNNING -> running += item(info)
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> waiting += item(info)
                    WorkInfo.State.FAILED -> if (info.id !in hidden) {
                        val it = item(info)
                        if (it.finishedAt == null || now - it.finishedAt <= DONE_WINDOW_MS) failed += it
                    }
                    WorkInfo.State.SUCCEEDED -> if (info.id !in hidden) {
                        val at = info.outputData.getLong(UploadWorker.KEY_FINISHED_AT, 0L)
                        if (at > 0 && now - at <= DONE_WINDOW_MS) done += item(info)
                    }
                    // Stopped by Android's foreground service limit: the worker leaves its retry payload in the progress.
                    WorkInfo.State.CANCELLED -> if (info.id !in hidden && info.progress.getString(UploadWorker.KEY_ERROR_MESSAGE) != null) {
                        val it = item(info, stopped = true)
                        if (it.finishedAt == null || now - it.finishedAt <= DONE_WINDOW_MS) failed += it
                    }
                }
            }
            val newestFirst = compareByDescending<QueueItem> { it.finishedAt ?: 0L }
            return QueueSections(
                running.sortedBy { it.enqueuedAt },
                waiting.sortedBy { it.enqueuedAt },
                failed.sortedWith(newestFirst),
                done.sortedWith(newestFirst),
            )
        }

        private fun tag(info: WorkInfo, prefix: String): String? =
            info.tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)

        private fun item(info: WorkInfo, stopped: Boolean = false): QueueItem {
            val out = if (stopped) info.progress else info.outputData
            val destination = (tag(info, UploadEnqueuer.TAG_DEST) ?: out.getString(UploadWorker.KEY_DESTINATION))
                ?.let { name -> UploadDestination.entries.firstOrNull { it.name == name } }
            val total = info.progress.getInt(UploadWorker.KEY_PROGRESS_TOTAL, 0)
            val done = info.progress.getInt(UploadWorker.KEY_PROGRESS_DONE, 0)
            val failed = info.state == WorkInfo.State.FAILED || stopped
            val isDuplicate = failed && out.getBoolean(UploadWorker.KEY_DUPLICATE_FOUND, false)
            val error = if (!failed) null else when {
                isDuplicate -> "Already uploaded"
                else -> out.getString(UploadWorker.KEY_ERROR_MESSAGE) ?: "Upload failed"
            }
            val retry = if (failed) UploadSpec.from(out) else null
            val duplicate = if (isDuplicate) DuplicateInfo(
                path = out.getString(UploadWorker.KEY_FILE_PATH).orEmpty(),
                url = out.getString(UploadWorker.KEY_DUPLICATE_URL),
                fileName = out.getString(UploadWorker.KEY_DUPLICATE_FILE_NAME),
                timestamp = out.getLong(UploadWorker.KEY_DUPLICATE_TIMESTAMP, 0L),
            ) else null
            val finishedAt = out.getLong(UploadWorker.KEY_FINISHED_AT, 0L).takeIf { it > 0 }
            return QueueItem(
                id = info.id,
                fileName = tag(info, UploadEnqueuer.TAG_NAME) ?: "Upload",
                fileCount = tag(info, UploadEnqueuer.TAG_COUNT)?.toIntOrNull() ?: 1,
                destination = destination,
                fraction = if (info.state == WorkInfo.State.RUNNING && total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null,
                error = error,
                finishedAt = finishedAt,
                retrySpec = retry,
                isRunning = info.state == WorkInfo.State.RUNNING,
                isActive = !info.state.isFinished,
                enqueuedAt = tag(info, UploadEnqueuer.TAG_AT)?.toLongOrNull() ?: 0L,
                duplicate = duplicate,
            )
        }
    }
}

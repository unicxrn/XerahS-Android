package com.xerahs.android.feature.upload.worker

import androidx.work.Data
import androidx.work.WorkInfo

/** Files that failed or stopped uploads may still retry, so cleanup must not delete them. */
object RetryFiles {
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Paths named by the retry payload of failed work (its output) and of work Android stopped
     * ([stopped], plus the progress older builds left on cancelled work) that finished within [maxAgeMs].
     */
    fun referencedPaths(
        infos: List<WorkInfo>,
        stopped: Collection<Data>,
        now: Long,
        maxAgeMs: Long = MAX_AGE_MS,
    ): Set<String> {
        val payloads = infos.mapNotNull { info ->
            when (info.state) {
                WorkInfo.State.FAILED -> info.outputData
                WorkInfo.State.CANCELLED -> info.progress.takeIf { it.getString(UploadWorker.KEY_ERROR_MESSAGE) != null }
                else -> null
            }
        } + stopped
        return payloads.flatMapTo(HashSet()) { data ->
            val at = data.getLong(UploadWorker.KEY_FINISHED_AT, 0L)
            // No time stamp: keep its files rather than guess.
            if (at > 0 && now - at > maxAgeMs) emptyList() else UploadSpec.from(data)?.paths.orEmpty()
        }
    }
}

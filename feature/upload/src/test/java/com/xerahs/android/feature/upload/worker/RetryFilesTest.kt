package com.xerahs.android.feature.upload.worker

import androidx.work.Data
import androidx.work.WorkInfo
import com.xerahs.android.core.domain.model.UploadDestination
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class RetryFilesTest {
    private val now = 100L * 24 * 60 * 60 * 1000
    private val day = 24L * 60 * 60 * 1000

    private fun payload(paths: List<String>, at: Long) = Data.Builder()
        .putAll(UploadEnqueuer.inputData(UploadSpec(paths, UploadDestination.FTP)))
        .putString(UploadWorker.KEY_ERROR_MESSAGE, "x")
        .putLong(UploadWorker.KEY_FINISHED_AT, at)
        .build()

    private fun info(state: WorkInfo.State, output: Data = Data.EMPTY, progress: Data = Data.EMPTY) =
        WorkInfo(UUID.randomUUID(), state, setOf(UPLOAD_WORK_TAG), output, progress)

    @Test fun keepsFilesOfRecentFailedAndStoppedWork() {
        val infos = listOf(
            info(WorkInfo.State.FAILED, payload(listOf("/c/1.png", "/c/2.png"), now - 6 * day)),
            info(WorkInfo.State.FAILED, payload(listOf("/c/old.png"), now - 8 * day)),
            info(WorkInfo.State.CANCELLED, progress = payload(listOf("/c/legacy.png"), now - day)),
            info(WorkInfo.State.SUCCEEDED, payload(listOf("/c/done.png"), now)),
            info(WorkInfo.State.CANCELLED),
        )
        val stopped = listOf(payload(listOf("/c/stopped.png"), now - day), payload(listOf("/c/stale.png"), now - 9 * day))

        assertEquals(
            setOf("/c/1.png", "/c/2.png", "/c/legacy.png", "/c/stopped.png"),
            RetryFiles.referencedPaths(infos, stopped, now),
        )
    }

    @Test fun failureWithoutPayloadKeepsNothing() {
        assertEquals(emptySet<String>(), RetryFiles.referencedPaths(listOf(info(WorkInfo.State.FAILED)), emptyList(), now))
    }
}

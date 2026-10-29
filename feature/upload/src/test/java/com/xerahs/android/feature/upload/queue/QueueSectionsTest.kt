package com.xerahs.android.feature.upload.queue

import androidx.work.Data
import androidx.work.WorkInfo
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.feature.upload.worker.UPLOAD_WORK_TAG
import com.xerahs.android.feature.upload.worker.UploadEnqueuer
import com.xerahs.android.feature.upload.worker.UploadSpec
import com.xerahs.android.feature.upload.worker.UploadWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class QueueSectionsTest {
    private val now = 1_000_000_000L
    private val hour = 3_600_000L
    private val spec = UploadSpec(listOf("/c/cat.png", "/c/dog.png"), UploadDestination.FTP)

    private fun info(
        state: WorkInfo.State,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
        s: UploadSpec = spec,
    ) = WorkInfo(
        UUID.randomUUID(), state,
        setOf(UPLOAD_WORK_TAG) + UploadEnqueuer.tagsFor(s),
        output, progress,
    )

    private fun failedOutput(at: Long) = Data.Builder()
        .putAll(UploadEnqueuer.inputData(spec.copy(paths = listOf("/c/dog.png"))))
        .putString(UploadWorker.KEY_ERROR_MESSAGE, "Connection refused")
        .putLong(UploadWorker.KEY_FINISHED_AT, at)
        .build()

    @Test fun mapsStatesToSections() {
        val running = info(WorkInfo.State.RUNNING, progress = Data.Builder()
            .putInt(UploadWorker.KEY_PROGRESS_DONE, 1).putInt(UploadWorker.KEY_PROGRESS_TOTAL, 2).build())
        val waiting = info(WorkInfo.State.ENQUEUED)
        val failed = info(WorkInfo.State.FAILED, failedOutput(now - hour))
        val done = info(WorkInfo.State.SUCCEEDED, Data.Builder().putLong(UploadWorker.KEY_FINISHED_AT, now - hour).build())
        val cancelled = info(WorkInfo.State.CANCELLED)

        val s = QueueSections.build(listOf(running, waiting, failed, done, cancelled), now, emptySet())

        assertEquals(listOf(running.id), s.running.map { it.id })
        assertEquals(listOf(waiting.id), s.waiting.map { it.id })
        assertEquals(listOf(failed.id), s.failed.map { it.id })
        assertEquals(listOf(done.id), s.done.map { it.id })
        assertEquals(0.5f, s.running.single().fraction!!, 0.001f)
        assertEquals("cat.png", s.running.single().fileName)
        assertEquals(2, s.running.single().fileCount)
        assertEquals(UploadDestination.FTP, s.running.single().destination)
    }

    @Test fun failureShowsErrorAndCanRetryRemainingFiles() {
        val failed = info(WorkInfo.State.FAILED, failedOutput(now - hour))
        val item = QueueSections.build(listOf(failed), now, emptySet()).failed.single()
        assertEquals("Connection refused", item.error)
        assertEquals(listOf("/c/dog.png"), item.retrySpec!!.paths)
    }

    @Test fun doneKeepsOnlyTheLast24Hours() {
        val recent = info(WorkInfo.State.SUCCEEDED, Data.Builder().putLong(UploadWorker.KEY_FINISHED_AT, now - 23 * hour).build())
        val old = info(WorkInfo.State.SUCCEEDED, Data.Builder().putLong(UploadWorker.KEY_FINISHED_AT, now - 25 * hour).build())
        val noStamp = info(WorkInfo.State.SUCCEEDED)
        val s = QueueSections.build(listOf(recent, old, noStamp), now, emptySet())
        assertEquals(listOf(recent.id), s.done.map { it.id })
    }

    @Test fun hiddenFinishedWorkIsDroppedButActiveWorkIsNot() {
        val failed = info(WorkInfo.State.FAILED, failedOutput(now))
        val running = info(WorkInfo.State.RUNNING)
        val s = QueueSections.build(listOf(failed, running), now, setOf(failed.id, running.id))
        assertTrue(s.failed.isEmpty())
        assertEquals(1, s.running.size)
    }

    @Test fun duplicateFailureRetriesWithoutTheDuplicateCheck() {
        val out = Data.Builder()
            .putBoolean(UploadWorker.KEY_DUPLICATE_FOUND, true)
            .putString(UploadWorker.KEY_FILE_PATH, "/c/cat.png")
            .putString(UploadWorker.KEY_DESTINATION, "FTP")
            .build()
        val item = QueueSections.build(listOf(info(WorkInfo.State.FAILED, out)), now, emptySet()).failed.single()
        assertEquals("Already uploaded", item.error)
        assertTrue(item.retrySpec!!.skipDuplicateCheck)
    }

    @Test fun nothingActiveMeansEmptyAndNoRetryWithoutOutput() {
        assertTrue(QueueSections.build(emptyList(), now, emptySet()).isEmpty)
        val bare = QueueSections.build(listOf(info(WorkInfo.State.FAILED)), now, emptySet()).failed.single()
        assertNull(bare.retrySpec)
        assertNotNull(bare.error)
    }
}

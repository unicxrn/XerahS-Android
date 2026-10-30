package com.xerahs.android.feature.upload.queue

import androidx.work.Data
import androidx.work.WorkInfo
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.feature.upload.worker.UPLOAD_WORK_TAG
import com.xerahs.android.feature.upload.worker.UploadEnqueuer
import com.xerahs.android.feature.upload.worker.UploadNotifier
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

    @Test fun duplicateFailureOffersUploadAnywayOnly() {
        val out = Data.Builder()
            .putBoolean(UploadWorker.KEY_DUPLICATE_FOUND, true)
            .putString(UploadWorker.KEY_FILE_PATH, "/c/cat.png")
            .putString(UploadWorker.KEY_DESTINATION, "FTP")
            .build()
        val item = QueueSections.build(listOf(info(WorkInfo.State.FAILED, out)), now, emptySet()).failed.single()
        assertEquals("Already uploaded", item.error)
        assertTrue(!item.retrySpec!!.skipDuplicateCheck)
        assertTrue(item.uploadAnywaySpec()!!.skipDuplicateCheck)
    }

    @Test fun nothingActiveMeansEmptyAndNoRetryWithoutOutput() {
        assertTrue(QueueSections.build(emptyList(), now, emptySet()).isEmpty)
        val bare = QueueSections.build(listOf(info(WorkInfo.State.FAILED)), now, emptySet()).failed.single()
        assertNull(bare.retrySpec)
        assertNotNull(bare.error)
    }
}

class QueueDuplicateRetryTest {
    private val now = 1_000_000_000L
    private val spec = UploadSpec(
        listOf("/c/1.png", "/c/2.png", "/c/3.png"), UploadDestination.S3,
        profileId = "p1", albumId = "a1", tagIds = listOf("t1"),
    )

    private fun duplicateItem(): QueueItem {
        // What the worker writes when file 2 is a duplicate: the full remaining spec plus the duplicate keys.
        val remaining = spec.copy(paths = listOf("/c/2.png", "/c/3.png"))
        val out = Data.Builder()
            .putAll(UploadEnqueuer.inputData(remaining))
            .putBoolean(UploadWorker.KEY_DUPLICATE_FOUND, true)
            .putString(UploadWorker.KEY_DUPLICATE_URL, "https://x/2.png")
            .putString(UploadWorker.KEY_FILE_PATH, "/c/2.png")
            .putLong(UploadWorker.KEY_FINISHED_AT, now)
            .build()
        val info = WorkInfo(UUID.randomUUID(), WorkInfo.State.FAILED, setOf(UPLOAD_WORK_TAG) + UploadEnqueuer.tagsFor(spec, now), out, Data.EMPTY)
        return QueueSections.build(listOf(info), now, emptySet()).failed.single()
    }

    @Test fun uploadAnywayKeepsFilesProfileAndAlbum() {
        val s = duplicateItem().uploadAnywaySpec()!!
        assertEquals(listOf("/c/2.png", "/c/3.png"), s.paths)
        assertEquals("p1", s.profileId)
        assertEquals("a1", s.albumId)
        assertTrue(s.skipDuplicateCheck)
    }

    @Test fun skipDropsOnlyTheDuplicate() {
        val item = duplicateItem()
        val s = item.skipFileSpec()!!
        assertEquals(listOf("/c/3.png"), s.paths)
        assertEquals("p1", s.profileId)
        assertEquals("a1", s.albumId)
        assertEquals(false, s.skipDuplicateCheck)
        assertEquals(2, item.remaining)
    }

    @Test fun plainRetryNeverSkipsTheDuplicateCheck() {
        val s = duplicateItem().retrySpec!!
        assertEquals(listOf("/c/2.png", "/c/3.png"), s.paths)
        assertEquals(false, s.skipDuplicateCheck)
    }

    @Test fun cancelledWorkWithRetryPayloadShowsAsFailed() {
        val payload = Data.Builder()
            .putAll(UploadEnqueuer.inputData(spec.copy(paths = listOf("/c/3.png"))))
            .putString(UploadWorker.KEY_ERROR_MESSAGE, "Upload stopped by Android after running too long")
            .putLong(UploadWorker.KEY_FINISHED_AT, now)
            .build()
        val info = WorkInfo(UUID.randomUUID(), WorkInfo.State.CANCELLED, setOf(UPLOAD_WORK_TAG), Data.EMPTY, payload)
        val item = QueueSections.build(listOf(info), now, emptySet()).failed.single()
        assertEquals(listOf("/c/3.png"), item.retrySpec!!.paths)
        assertEquals(UploadWorker.STOPPED_MESSAGE, item.error)
    }

    @Test fun waitingSortsByEnqueueTimeAndFailedHonoursTheWindow() {
        fun waiting(at: Long) = WorkInfo(UUID.randomUUID(), WorkInfo.State.ENQUEUED,
            setOf(UPLOAD_WORK_TAG) + UploadEnqueuer.tagsFor(spec, at), Data.EMPTY, Data.EMPTY)
        val late = waiting(30); val early = waiting(10)
        val old = WorkInfo(UUID.randomUUID(), WorkInfo.State.FAILED, setOf(UPLOAD_WORK_TAG), Data.Builder()
            .putLong(UploadWorker.KEY_FINISHED_AT, now - 25 * 3_600_000L).build(), Data.EMPTY)
        val s = QueueSections.build(listOf(late, early, old), now, emptySet())
        assertEquals(listOf(early.id, late.id), s.waiting.map { it.id })
        assertTrue(s.failed.isEmpty())
    }

    @Test fun failedWorkThatARetryReplacedIsHidden() {
        val out = Data.Builder()
            .putAll(UploadEnqueuer.inputData(spec))
            .putString(UploadWorker.KEY_ERROR_MESSAGE, "Connection refused")
            .putLong(UploadWorker.KEY_FINISHED_AT, now)
            .build()
        val failed = WorkInfo(UUID.randomUUID(), WorkInfo.State.FAILED, setOf(UPLOAD_WORK_TAG), out, Data.EMPTY)
        val other = WorkInfo(UUID.randomUUID(), WorkInfo.State.FAILED, setOf(UPLOAD_WORK_TAG), out, Data.EMPTY)
        // The retry the notification enqueued: the queue has no in-memory record of it.
        val retry = WorkInfo(UUID.randomUUID(), WorkInfo.State.ENQUEUED,
            setOf(UPLOAD_WORK_TAG, UploadEnqueuer.TAG_RETRY_OF + failed.id) + UploadEnqueuer.tagsFor(spec, now), Data.EMPTY, Data.EMPTY)

        val s = QueueSections.build(listOf(failed, other, retry), now, emptySet())
        assertEquals(listOf(other.id), s.failed.map { it.id })
        assertEquals(listOf(retry.id), s.waiting.map { it.id })
        assertEquals(setOf(failed.id), QueueSections.retriedIds(listOf(failed, other, retry)))
    }

    @Test fun retryIsUniquePerOldWorkAndUnwatched() {
        val old = UUID.randomUUID()
        assertEquals("retry-$old", UploadEnqueuer.retryName(old))
        val data = UploadEnqueuer.inputData(spec.copy(background = true))
        assertTrue(UploadSpec.from(data)!!.background)
    }

    @Test fun partialBatchFailureSaysHowManyMadeIt() {
        assertEquals("2 of 5 uploaded. Timeout", UploadNotifier.failureText("Timeout", 2, 5))
        assertEquals("Timeout", UploadNotifier.failureText("Timeout", 0, 5))
        assertEquals("Timeout", UploadNotifier.failureText("Timeout", 1, 1))
    }

    @Test fun nameTrimDoesNotSplitSurrogatePairs() {
        val name = "a".repeat(79) + "😀"
        assertEquals(79, UploadEnqueuer.trimName(name).length)
    }
}

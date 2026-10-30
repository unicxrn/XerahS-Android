package com.xerahs.android.feature.upload.worker

import androidx.work.Data
import androidx.work.WorkInfo
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.feature.upload.queue.QueueSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class StoppedUploadStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val spec = UploadSpec(
        listOf("/c/a b.png", "/c/ü=2.png"), UploadDestination.S3,
        profileId = "p1", albumId = "a1", tagIds = listOf("t1", "t2"),
        inputValues = mapOf("title" to "x=y:z"), background = true,
    )

    @Test fun roundTripsTheRetrySpecMessageAndTime() {
        val store = StoppedUploadStore(tmp.root)
        val id = UUID.randomUUID()
        store.save(id, spec, UploadWorker.STOPPED_MESSAGE, 1234L)

        val data = StoppedUploadStore(tmp.root).read(id)!!
        assertEquals(spec, UploadSpec.from(data))
        assertEquals(UploadWorker.STOPPED_MESSAGE, data.getString(UploadWorker.KEY_ERROR_MESSAGE))
        assertEquals(1234L, data.getLong(UploadWorker.KEY_FINISHED_AT, 0L))
        assertEquals(mapOf(id to data), store.all())
    }

    @Test fun nothingLeftStillRecordsTheStop() {
        val store = StoppedUploadStore(tmp.root)
        val id = UUID.randomUUID()
        store.save(id, null, "m", 5L)
        assertNull(UploadSpec.from(store.read(id)!!))
    }

    @Test fun clearRemovesOnlyThatWorkAndLeavesBatchProgressAlone() {
        val store = StoppedUploadStore(tmp.root)
        val progress = BatchProgressStore(tmp.root)
        val id = UUID.randomUUID()
        val other = UUID.randomUUID()
        progress.markDone(id, "/c/done.png")
        store.save(id, spec, "m", 1L)
        store.save(other, spec, "m", 1L)

        assertEquals(setOf("/c/done.png"), progress.completed(id))
        store.clear(id)
        assertNull(store.read(id))
        assertEquals(setOf(other), store.all().keys)
        assertEquals(setOf("/c/done.png"), progress.completed(id))
    }

    @Test fun missingOrUnrelatedFilesAreIgnored() {
        val store = StoppedUploadStore(tmp.root)
        assertNull(store.read(UUID.randomUUID()))
        tmp.newFile("not-a-uuid.stopped")
        assertTrue(store.all().isEmpty())
    }

    @Test fun queueShowsAStoppedWorkFromTheFileUnderFailed() {
        val store = StoppedUploadStore(tmp.root)
        val id = UUID.randomUUID()
        store.save(id, spec.copy(paths = listOf("/c/a b.png")), UploadWorker.STOPPED_MESSAGE, 1_000L)
        // Progress is empty: WorkManager dropped it with the cancel.
        val info = WorkInfo(id, WorkInfo.State.CANCELLED, setOf(UPLOAD_WORK_TAG), Data.EMPTY, Data.EMPTY)

        val item = QueueSections.build(listOf(info), 2_000L, emptySet(), store.all()).failed.single()
        assertEquals(UploadWorker.STOPPED_MESSAGE, item.error)
        assertEquals(listOf("/c/a b.png"), item.retrySpec!!.paths)
        assertTrue(QueueSections.build(listOf(info), 2_000L, emptySet()).isEmpty)
    }
}

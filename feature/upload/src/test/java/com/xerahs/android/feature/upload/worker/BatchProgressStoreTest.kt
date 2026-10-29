package com.xerahs.android.feature.upload.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class BatchProgressStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun retrySkipsCompletedPaths() {
        val store = BatchProgressStore(tmp.root)
        val id = UUID.randomUUID()
        store.markDone(id, "/a/1.png")
        store.markDone(id, "/a/2.png")
        // A fresh instance stands in for the retried worker reading the record back.
        assertEquals(setOf("/a/1.png", "/a/2.png"), BatchProgressStore(tmp.root).completed(id))
        val toUpload = listOf("/a/1.png", "/a/2.png", "/a/3.png").filterNot { it in store.completed(id) }
        assertEquals(listOf("/a/3.png"), toUpload)
    }

    @Test fun clearRemovesRecord() {
        val store = BatchProgressStore(tmp.root)
        val id = UUID.randomUUID()
        store.markDone(id, "/a/1.png")
        store.clear(id)
        assertTrue(store.completed(id).isEmpty())
        assertTrue(tmp.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun recordsAreKeptPerWork() {
        val store = BatchProgressStore(tmp.root)
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        store.markDone(a, "/a/1.png")
        assertTrue(store.completed(b).isEmpty())
    }

    @Test fun keepsUrlsOfFinishedFilesInOrder() {
        val store = BatchProgressStore(tmp.root)
        val id = UUID.randomUUID()
        store.markDone(id, "/a/1.png", "https://x/1")
        store.markDone(id, "/a/2.png", null)
        store.markDone(id, "/a/3.png", "https://x/3")
        assertEquals(listOf("https://x/1", "https://x/3"), store.urls(id))
    }

    @Test fun recordsHashesSoTheyAreSkippedByDuplicateDetection() {
        val store = BatchProgressStore(tmp.root)
        val id = UUID.randomUUID()
        store.markDone(id, "/a/1.png", "https://x/1", hash = "abc")
        store.markDone(id, "/a/2.png", null, hash = "def")
        assertEquals(setOf("abc", "def"), BatchProgressStore(tmp.root).hashes(id))
        assertEquals(setOf("/a/1.png", "/a/2.png"), store.completed(id))
    }

    @Test fun dropsAHalfWrittenLastLine() {
        val store = BatchProgressStore(tmp.root)
        val id = UUID.randomUUID()
        store.markDone(id, "/a/1.png", "https://x/1", hash = "abc")
        java.io.File(tmp.root, id.toString()).appendText("/a/2.p")
        assertEquals(setOf("/a/1.png"), store.completed(id))
        store.markDone(id, "/a/3.png")
        assertEquals(setOf("/a/1.png", "/a/3.png"), store.completed(id))
    }

    @Test fun pruneDeletesRecordsOlderThanAWeek() {
        val store = BatchProgressStore(tmp.root)
        val old = UUID.randomUUID()
        val fresh = UUID.randomUUID()
        store.markDone(old, "/a/1.png")
        store.markDone(fresh, "/a/2.png")
        val now = System.currentTimeMillis()
        java.io.File(tmp.root, old.toString()).setLastModified(now - 8L * 24 * 60 * 60 * 1000)
        store.prune(now)
        assertTrue(store.completed(old).isEmpty())
        assertEquals(setOf("/a/2.png"), store.completed(fresh))
    }
}

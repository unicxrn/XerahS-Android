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
}

package com.xerahs.android.feature.upload.worker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class SuccessBookkeepingTest {
    @Test fun marksDoneBeforeWritingHistory() = runBlocking {
        val calls = mutableListOf<String>()
        recordSuccess(
            markDone = { calls += "markDone" },
            insertHistory = { calls += "insert" },
            addTags = { calls += "tags" },
        )
        assertEquals(listOf("markDone", "insert", "tags"), calls)
    }

    @Test fun finishesEvenWhenTheWorkIsCancelledHalfway() = runBlocking {
        val calls = mutableListOf<String>()
        val inInsert = CompletableDeferred<Unit>()
        val job = async {
            recordSuccess(
                markDone = { calls += "markDone" },
                insertHistory = {
                    inInsert.complete(Unit)
                    yield(); yield()
                    calls += "insert"
                },
                addTags = { calls += "tags" },
            )
        }
        inInsert.await()
        job.cancel()
        runCatching { job.await() }
        assertEquals(listOf("markDone", "insert", "tags"), calls)
    }
}

package com.xerahs.android.feature.upload.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class UploadNotifierTest {
    @Test fun idsStayInTheirRangeAndDifferPerWork() {
        val ids = (1..500).map { UploadNotifier.notificationIdFor(UUID.randomUUID()) }
        assertTrue(ids.all { it in 2000 until 102000 })
        assertTrue(ids.toSet().size > 490)
    }

    @Test fun idIsStableForTheSameWork() {
        val id = UUID.randomUUID()
        assertEquals(UploadNotifier.notificationIdFor(id), UploadNotifier.notificationIdFor(id))
    }

    @Test fun progressIdNeverClashesWithResultId() {
        val id = UUID.randomUUID()
        assertNotEquals(UploadNotifier.notificationIdFor(id), UploadNotifier.progressIdFor(id))
        assertTrue(UploadNotifier.progressIdFor(id) >= 102000)
    }
}

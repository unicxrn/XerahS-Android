package com.xerahs.android.feature.upload.worker

import com.xerahs.android.core.domain.model.UploadDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadEnqueuerTest {
    private val spec = UploadSpec(
        paths = listOf("/c/1.png", "/c/2.png"),
        destination = UploadDestination.SFTP,
        profileId = "p1",
        albumId = "a1",
        tagIds = listOf("t1", "t2"),
        inputValues = mapOf("Token" to "x|y"),
    )

    @Test fun specSurvivesTheWorkData() {
        assertEquals(spec, UploadSpec.from(UploadEnqueuer.inputData(spec)))
    }

    @Test fun singleFileUsesTheLegacyKey() {
        val one = spec.copy(paths = listOf("/c/1.png"))
        val data = UploadEnqueuer.inputData(one)
        assertEquals("/c/1.png", data.getString(UploadWorker.KEY_FILE_PATH))
        assertEquals(one, UploadSpec.from(data))
    }

    @Test fun retryRebuildsFromStringExtras() {
        val extras = UploadEnqueuer.inputData(spec).keyValueMap.mapValues { it.value?.toString() }
        assertEquals(spec, UploadSpec.fromStrings(extras))
    }

    @Test fun missingOrUnknownDestinationGivesNoSpec() {
        assertNull(UploadSpec.fromStrings(mapOf(UploadWorker.KEY_FILE_PATHS to "/c/1.png")))
        assertNull(UploadSpec.fromStrings(mapOf(UploadWorker.KEY_FILE_PATHS to "/c/1.png", UploadWorker.KEY_DESTINATION to "NOPE")))
    }
}

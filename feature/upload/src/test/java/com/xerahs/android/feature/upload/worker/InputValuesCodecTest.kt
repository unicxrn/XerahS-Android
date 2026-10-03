package com.xerahs.android.feature.upload.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class InputValuesCodecTest {
    @Test fun roundTripsIncludingSeparatorsInValues() {
        val values = mapOf("Token" to "a|b=c", "Album" to "", "Multi\nline" to "x\ny")
        assertEquals(values, UploadWorker.decodeInputValues(UploadWorker.encodeInputValues(values)))
    }

    @Test fun nullOrBlankDecodesToEmpty() {
        assertEquals(emptyMap<String, String>(), UploadWorker.decodeInputValues(null))
        assertEquals(emptyMap<String, String>(), UploadWorker.decodeInputValues(""))
    }
}

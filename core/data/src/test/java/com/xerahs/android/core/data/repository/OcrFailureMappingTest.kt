package com.xerahs.android.core.data.repository

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.mlkit.common.MlKitException
import com.xerahs.android.core.domain.repository.OcrException
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFailureMappingTest {
    @Test
    fun missingPlayServicesIsUnavailable() {
        assertTrue(mapOcrFailure(false, RuntimeException("x")) is OcrException.Unavailable)
    }

    @Test
    fun mlKitUnavailableWithPlayServicesIsDownloading() {
        val e = MlKitException("model", MlKitException.UNAVAILABLE)
        assertTrue(mapOcrFailure(true, e) is OcrException.Downloading)
    }

    @Test
    fun apiNotConnectedIsUnavailable() {
        val e = ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED))
        assertTrue(mapOcrFailure(true, e) is OcrException.Unavailable)
    }

    @Test
    fun otherApiExceptionFallsThrough() {
        val e = ApiException(Status(CommonStatusCodes.NETWORK_ERROR))
        assertSame(e, mapOcrFailure(true, e))
    }

    @Test
    fun otherErrorsFallThrough() {
        val e = RuntimeException("boom")
        assertSame(e, mapOcrFailure(true, e))
    }
}

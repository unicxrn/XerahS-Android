package com.xerahs.android.core.domain.model

import com.xerahs.android.core.common.sxcu.CustomDestinationType.FILE
import com.xerahs.android.core.common.sxcu.CustomDestinationType.IMAGE
import com.xerahs.android.core.common.sxcu.CustomDestinationType.TEXT
import com.xerahs.android.core.common.sxcu.CustomDestinationType.URL_SHORTENER
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationCapabilitiesTest {
    private fun accepts(d: UploadDestination, mime: String, types: Set<com.xerahs.android.core.common.sxcu.CustomDestinationType>? = null) =
        DestinationCapabilities.accepts(d, mime, types)

    @Test fun imgurTakesImagesOnly() {
        assertTrue(accepts(UploadDestination.IMGUR, "image/png"))
        assertFalse(accepts(UploadDestination.IMGUR, "application/pdf"))
        assertFalse(accepts(UploadDestination.IMGUR, "text/plain"))
    }

    @Test fun storageDestinationsTakeAnything() {
        listOf(UploadDestination.S3, UploadDestination.FTP, UploadDestination.SFTP, UploadDestination.LOCAL).forEach {
            assertTrue(accepts(it, "application/zip"))
        }
    }

    @Test fun customUploaderFollowsDestinationTypes() {
        assertTrue(accepts(UploadDestination.CUSTOM_HTTP, "image/png", setOf(IMAGE)))
        assertFalse(accepts(UploadDestination.CUSTOM_HTTP, "application/pdf", setOf(IMAGE)))
        assertTrue(accepts(UploadDestination.CUSTOM_HTTP, "application/pdf", setOf(FILE)))
        assertTrue(accepts(UploadDestination.CUSTOM_HTTP, "image/png", setOf(FILE)))
        assertTrue(accepts(UploadDestination.CUSTOM_HTTP, "text/plain", setOf(TEXT)))
        assertFalse(accepts(UploadDestination.CUSTOM_HTTP, "text/plain", setOf(IMAGE)))
        assertFalse(accepts(UploadDestination.CUSTOM_HTTP, "image/png", setOf(URL_SHORTENER)))
    }

    @Test fun unknownCustomTypesAreAllowed() =
        assertTrue(accepts(UploadDestination.CUSTOM_HTTP, "video/mp4", null))

    @Test fun batchNeedsEveryFileAccepted() {
        val mimes = listOf("image/png", "application/pdf")
        assertFalse(DestinationCapabilities.acceptsAll(UploadDestination.IMGUR, mimes))
        assertTrue(DestinationCapabilities.acceptsAll(UploadDestination.S3, mimes))
    }
}

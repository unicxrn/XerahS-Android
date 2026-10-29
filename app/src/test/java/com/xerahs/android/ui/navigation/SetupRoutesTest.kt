package com.xerahs.android.ui.navigation

import com.xerahs.android.core.domain.model.UploadDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SetupRoutesTest {
    @Test
    fun imgurAndLocalNeedNoSetup() {
        assertNull(setupRouteFor(UploadDestination.IMGUR))
        assertNull(setupRouteFor(UploadDestination.LOCAL))
    }

    @Test
    fun everyOtherDestinationHasARoute() {
        UploadDestination.values()
            .filter { it != UploadDestination.IMGUR && it != UploadDestination.LOCAL }
            .forEach { assertNotNull(it.name, setupRouteFor(it)) }
    }

    @Test
    fun specificRoutes() {
        assertEquals("settings/s3", setupRouteFor(UploadDestination.S3))
        assertEquals("settings/ftp", setupRouteFor(UploadDestination.SFTP))
        assertEquals("settings/import-uploader", setupRouteFor(UploadDestination.CUSTOM_HTTP))
        assertEquals("settings/destination/IMMICH", setupRouteFor(UploadDestination.IMMICH))
    }
}

package com.xerahs.android.feature.settings.destinations

import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeDestinationFormsTest {
    @Test fun roundTripsEveryConfig() {
        listOf(
            UploadConfig.NextcloudConfig("https://nc.test", "alice", "pw", "Shots", false),
            UploadConfig.ImmichConfig("https://im.test", "key", false),
            UploadConfig.GistConfig("tok", true),
        ).forEach { config ->
            val destination = NativeDestinationForms.destinationOf(config)!!
            assertEquals(config, NativeDestinationForms.toConfig(destination, NativeDestinationForms.toValues(config)))
        }
    }

    @Test fun missingValuesFallBackToDefaults() {
        assertEquals(UploadConfig.NextcloudConfig(), NativeDestinationForms.toConfig(UploadDestination.NEXTCLOUD, emptyMap()))
        assertEquals(UploadConfig.GistConfig(), NativeDestinationForms.toConfig(UploadDestination.GITHUB_GIST, emptyMap()))
    }

    @Test fun reportsMissingRequiredFields() {
        assertEquals(
            listOf("Username", "App password"),
            NativeDestinationForms.missingRequired(UploadDestination.NEXTCLOUD, mapOf("serverUrl" to "https://nc.test"))
        )
    }
}

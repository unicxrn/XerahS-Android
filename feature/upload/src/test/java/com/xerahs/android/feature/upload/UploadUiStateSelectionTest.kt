package com.xerahs.android.feature.upload

import com.xerahs.android.core.common.sxcu.CustomDestinationType
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadUiStateSelectionTest {

    private val fileProfile = UploadProfile("p-file", "Files", UploadDestination.CUSTOM_HTTP, createdAt = 0)
    private val imageProfile = UploadProfile("p-img", "Images", UploadDestination.CUSTOM_HTTP, createdAt = 0)

    private val pdfState = UploadUiState(
        selectedDestination = UploadDestination.CUSTOM_HTTP,
        fileMimeTypes = listOf("application/pdf"),
        globalCustomTypes = setOf(CustomDestinationType.IMAGE),
        profiles = listOf(imageProfile, fileProfile),
        profileCustomTypes = mapOf(
            imageProfile.id to setOf(CustomDestinationType.IMAGE),
            fileProfile.id to setOf(CustomDestinationType.FILE),
        ),
    )

    @Test
    fun customRowShownWhenOnlyAProfileAllowsTheFile() {
        assertFalse(pdfState.allows(UploadDestination.CUSTOM_HTTP))
        assertTrue(UploadDestination.CUSTOM_HTTP in pdfState.selectableDestinations)
        assertFalse(UploadDestination.IMGUR in pdfState.selectableDestinations)
        assertEquals(listOf(fileProfile), pdfState.allowedProfiles(UploadDestination.CUSTOM_HTTP))
    }

    @Test
    fun pickingDisallowedGlobalSelectsAllowedProfile() {
        assertEquals(UploadDestination.CUSTOM_HTTP to fileProfile.id, pdfState.resolveDestination(UploadDestination.CUSTOM_HTTP))
        assertEquals(UploadDestination.S3 to null, pdfState.resolveDestination(UploadDestination.S3))
    }

    @Test
    fun fallbackPrefersProfileOfSameDestination() {
        assertFalse(pdfState.isSelectionAllowed)
        assertEquals(UploadDestination.CUSTOM_HTTP to fileProfile.id, pdfState.fallbackSelection())
    }

    @Test
    fun fallbackMovesToAnotherDestinationWhenNoProfileFits() {
        val state = pdfState.copy(selectedDestination = UploadDestination.IMGUR, profiles = emptyList())
        assertFalse(state.isSelectionAllowed)
        val (dest, profileId) = state.fallbackSelection()
        assertTrue(state.allows(dest))
        assertEquals(null, profileId)
    }
}

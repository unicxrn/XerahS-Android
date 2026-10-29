package com.xerahs.android.ui.intake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntakeUriPolicyTest {
    private val own = setOf("com.xerahs.android.fileprovider")

    @Test
    fun rejectsFileScheme() {
        assertFalse(IntakeUriPolicy.isAllowed("file", "", own))
        assertFalse(IntakeUriPolicy.isAllowed("FILE", null, own))
    }

    @Test
    fun rejectsOwnFileProviderAuthority() {
        assertFalse(IntakeUriPolicy.isAllowed("content", "com.xerahs.android.fileprovider", own))
        assertFalse(IntakeUriPolicy.isAllowed("content", "COM.xerahs.android.FileProvider", own))
        assertFalse(IntakeUriPolicy.isAllowed("content", "someone@com.xerahs.android.fileprovider", own))
    }

    @Test
    fun allowsForeignContentUri() {
        assertTrue(IntakeUriPolicy.isAllowed("content", "com.android.providers.media.documents", own))
    }

    @Test
    fun rejectsNullOrEmptyScheme() {
        assertFalse(IntakeUriPolicy.isAllowed(null, "com.example.provider", own))
        assertFalse(IntakeUriPolicy.isAllowed("", "com.example.provider", own))
    }
}

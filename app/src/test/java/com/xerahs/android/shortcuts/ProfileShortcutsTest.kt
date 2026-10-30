package com.xerahs.android.shortcuts

import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileShortcutsTest {
    private fun profile(id: String, name: String = id) =
        UploadProfile(id = id, name = name, destination = UploadDestination.S3, createdAt = 0)

    private fun upload(profileId: String?, at: Long) = HistoryItem(
        id = "h$at", filePath = "/f", uploadDestination = UploadDestination.S3,
        timestamp = at, fileName = "f", profileId = profileId
    )

    @Test fun idRoundTrips() = assertEquals("abc-1", ProfileShortcuts.profileIdOf(ProfileShortcuts.shortcutId("abc-1")))
    @Test fun idKeepsColonsInProfileId() = assertEquals("a:b", ProfileShortcuts.profileIdOf("profile:a:b"))
    @Test fun otherShortcutsAreNotProfiles() = assertNull(ProfileShortcuts.profileIdOf("copy_last_link"))
    @Test fun emptyIdIsNotAProfile() = assertNull(ProfileShortcuts.profileIdOf("profile:"))
    @Test fun nullIdIsNotAProfile() = assertNull(ProfileShortcuts.profileIdOf(null))

    @Test fun firstFourWithoutHistory() {
        val profiles = (1..6).map { profile("p$it") }
        assertEquals(listOf("p1", "p2", "p3", "p4"), ProfileShortcuts.choose(profiles, emptyMap()).map { it.id })
    }

    @Test fun mostRecentlyUsedFirstThenListOrder() {
        val profiles = (1..6).map { profile("p$it") }
        val used = mapOf("p5" to 10L, "p2" to 30L, "p6" to 20L)
        assertEquals(listOf("p2", "p6", "p5", "p1"), ProfileShortcuts.choose(profiles, used).map { it.id })
    }

    @Test fun fewerThanFourKeepsAll() =
        assertEquals(2, ProfileShortcuts.choose(listOf(profile("a"), profile("b")), emptyMap()).size)

    @Test fun lastUsedTakesNewestPerProfile() {
        val history = listOf(upload("a", 5), upload("a", 9), upload("b", 3), upload(null, 100))
        assertEquals(mapOf("a" to 9L, "b" to 3L), ProfileShortcuts.lastUsed(history))
    }

    @Test fun blankNameFallsBackToDestination() = assertEquals("S3", ProfileShortcuts.shortLabel(profile("x", " ")))
}

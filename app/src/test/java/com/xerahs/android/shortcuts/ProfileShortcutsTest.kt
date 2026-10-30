package com.xerahs.android.shortcuts

import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileShortcutsTest {
    private fun profile(id: String, name: String = id) =
        UploadProfile(id = id, name = name, destination = UploadDestination.S3, createdAt = 0)

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

    @Test fun unchangedEntriesAreNotRepublished() {
        val desired = ProfileShortcuts.entries(listOf(profile("a"), profile("b")))
        assertEquals(emptyList<ProfileShortcuts.Entry>(), ProfileShortcuts.changed(desired, desired))
    }

    @Test fun renamedOrMovedEntriesAreRepublished() {
        val published = ProfileShortcuts.entries(listOf(profile("a"), profile("b"), profile("c")))
        val desired = ProfileShortcuts.entries(listOf(profile("b"), profile("a"), profile("c", "Cee"), profile("d")))
        assertEquals(listOf("profile:b", "profile:a", "profile:c", "profile:d"), ProfileShortcuts.changed(desired, published).map { it.id })
        val same = ProfileShortcuts.entries(listOf(profile("a"), profile("b"), profile("c", "Cee")))
        assertEquals(listOf("profile:c"), ProfileShortcuts.changed(same, published).map { it.id })
    }

    @Test fun staleKeepsOtherShortcuts() {
        val desired = ProfileShortcuts.entries(listOf(profile("a")))
        assertEquals(listOf("profile:gone"), ProfileShortcuts.stale(desired, listOf("profile:a", "profile:gone", "copy_last_link", "profile:gone")))
    }

    @Test fun blankNameFallsBackToDestination() = assertEquals("S3", ProfileShortcuts.shortLabel(profile("x", " ")))
}

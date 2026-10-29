package com.xerahs.android.ui

import com.xerahs.android.core.domain.model.BiometricLockMode
import com.xerahs.android.core.domain.model.BiometricLockMode.LOCK_APP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    @Test
    fun staysOpenWhenUnlockedAndWithinTimeout() =
        assertFalse(LockPolicy.shouldPrompt(LOCK_APP, true, 10_000, 60_000))

    @Test
    fun promptsWhenUnlockedAndTimeoutPassed() =
        assertTrue(LockPolicy.shouldPrompt(LOCK_APP, true, 60_000, 60_000))

    @Test
    fun promptsWithZeroTimeout() =
        assertTrue(LockPolicy.shouldPrompt(LOCK_APP, true, 0, 0))

    @Test
    fun promptsWhenBackgroundedWhileLockedEvenWithinTimeout() =
        assertTrue(LockPolicy.shouldPrompt(LOCK_APP, false, 1_000, 60_000))

    @Test
    fun neverPromptsWhenLockIsOffOrUnknown() {
        assertFalse(LockPolicy.shouldPrompt(BiometricLockMode.OFF, false, 0, 0))
        assertFalse(LockPolicy.shouldPrompt(BiometricLockMode.LOCK_CREDENTIALS, false, 0, 0))
        assertFalse(LockPolicy.shouldPrompt(null, false, 0, 0))
    }

    @Test
    fun fromNameIsCaseInsensitiveAndFailsClosed() {
        assertEquals(LOCK_APP, BiometricLockMode.fromName("lock_app"))
        assertEquals(BiometricLockMode.OFF, BiometricLockMode.fromName("off"))
        assertEquals(BiometricLockMode.OFF, BiometricLockMode.fromName(null))
        assertEquals(BiometricLockMode.OFF, BiometricLockMode.fromName(" "))
        assertEquals(LOCK_APP, BiometricLockMode.fromName("SOMETHING_NEW"))
    }
}

package com.xerahs.android.ui

import com.xerahs.android.core.domain.model.BiometricLockMode

/** Pure decision for the app lock, so it can be unit tested. */
object LockPolicy {

    /**
     * True when the lock screen must be shown and the user asked to authenticate.
     * The app stays open only if it went to the background unlocked and came back within
     * [timeoutMs]. Leaving the lock screen never counts as unlocked.
     */
    fun shouldPrompt(
        mode: BiometricLockMode?,
        wasUnlockedWhenBackgrounded: Boolean,
        elapsedMs: Long,
        timeoutMs: Long
    ): Boolean {
        if (mode != BiometricLockMode.LOCK_APP) return false
        return !(wasUnlockedWhenBackgrounded && elapsedMs < timeoutMs)
    }
}

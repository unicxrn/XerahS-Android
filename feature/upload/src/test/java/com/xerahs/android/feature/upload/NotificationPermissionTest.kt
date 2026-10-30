package com.xerahs.android.feature.upload

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionTest {
    @Test fun asksOnFirstUploadOnAndroid13() =
        assertTrue(shouldAskNotificationPermission(sdk = 33, granted = false, alreadyAsked = false))

    @Test fun neverAsksTwice() =
        assertFalse(shouldAskNotificationPermission(sdk = 34, granted = false, alreadyAsked = true))

    @Test fun skipsWhenGranted() =
        assertFalse(shouldAskNotificationPermission(sdk = 34, granted = true, alreadyAsked = false))

    @Test fun skipsBeforeAndroid13() =
        assertFalse(shouldAskNotificationPermission(sdk = 32, granted = false, alreadyAsked = false))
}

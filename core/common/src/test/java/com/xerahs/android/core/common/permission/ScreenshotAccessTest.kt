package com.xerahs.android.core.common.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotAccessTest {
    @Test fun fullOn33() =
        assertEquals(ScreenshotAccess.FULL, screenshotAccessFor(33, images = true, userSelected = false, legacyRead = false))

    @Test fun limitedOn34() =
        assertEquals(ScreenshotAccess.LIMITED, screenshotAccessFor(34, images = false, userSelected = true, legacyRead = false))

    @Test fun fullBeatsLimitedOn34() =
        assertEquals(ScreenshotAccess.FULL, screenshotAccessFor(34, images = true, userSelected = true, legacyRead = false))

    @Test fun deniedOn34() =
        assertEquals(ScreenshotAccess.DENIED, screenshotAccessFor(34, images = false, userSelected = false, legacyRead = false))

    @Test fun userSelectedIgnoredOn33() =
        assertEquals(ScreenshotAccess.DENIED, screenshotAccessFor(33, images = false, userSelected = true, legacyRead = false))

    @Test fun legacyOn32() =
        assertEquals(ScreenshotAccess.FULL, screenshotAccessFor(32, images = false, userSelected = false, legacyRead = true))

    @Test fun legacyDeniedOn32() =
        assertEquals(ScreenshotAccess.DENIED, screenshotAccessFor(32, images = true, userSelected = false, legacyRead = false))
}

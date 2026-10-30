package com.xerahs.android.feature.upload.screenshots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotPolicyTest {
    @Test fun dcimScreenshots() = assertTrue(isScreenshot("DCIM/Screenshots/", null))
    @Test fun picturesScreenshots() = assertTrue(isScreenshot("Pictures/Screenshots/", null))
    @Test fun caseInsensitive() = assertTrue(isScreenshot("pictures/screenshots/", null))
    @Test fun plainPictures() = assertFalse(isScreenshot("Pictures/", null))
    @Test fun nullPathBucketMatches() = assertTrue(isScreenshot(null, "Screenshots"))
    @Test fun nullPathOtherBucket() = assertFalse(isScreenshot(null, "Camera"))
    @Test fun bothNull() = assertFalse(isScreenshot(null, null))

    @Test fun notificationIdsDifferPerImage() {
        assertTrue(ScreenshotNotifier.notificationIdFor(1L) != ScreenshotNotifier.notificationIdFor(2L))
        assertEquals(ScreenshotNotifier.notificationIdFor(7L), ScreenshotNotifier.notificationIdFor(7L))
    }
}

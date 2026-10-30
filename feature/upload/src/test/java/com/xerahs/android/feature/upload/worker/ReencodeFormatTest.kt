package com.xerahs.android.feature.upload.worker

import com.xerahs.android.core.domain.model.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ReencodeFormatTest {
    @Test
    fun originalKeepsPngForPngSources() {
        assertEquals(ReencodeFormat.PNG, ReencodeFormat.choose(ImageFormat.ORIGINAL, "image/png", hasAlpha = true))
        assertEquals(ReencodeFormat.PNG, ReencodeFormat.choose(ImageFormat.ORIGINAL, "image/png", hasAlpha = false))
    }

    @Test
    fun originalKeepsTransparentWebpAsPng() {
        assertEquals(ReencodeFormat.PNG, ReencodeFormat.choose(ImageFormat.ORIGINAL, "image/webp", hasAlpha = true))
    }

    @Test
    fun originalUsesJpegForOpaqueSources() {
        assertEquals(ReencodeFormat.JPEG, ReencodeFormat.choose(ImageFormat.ORIGINAL, "image/jpeg", hasAlpha = false))
        assertEquals(ReencodeFormat.JPEG, ReencodeFormat.choose(ImageFormat.ORIGINAL, "image/webp", hasAlpha = false))
    }

    @Test
    fun explicitChoicesWin() {
        assertEquals(ReencodeFormat.JPEG, ReencodeFormat.choose(ImageFormat.JPEG, "image/png", hasAlpha = true))
        assertEquals(ReencodeFormat.WEBP, ReencodeFormat.choose(ImageFormat.WEBP, "image/png", hasAlpha = true))
        assertEquals("jpg", ReencodeFormat.JPEG.extension)
    }
}

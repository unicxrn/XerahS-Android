package com.xerahs.android.feature.upload.screenshots

/** True for images saved to a Screenshots folder (API 29+ relative path) or a bucket named Screenshots. */
fun isScreenshot(relativePath: String?, bucket: String?): Boolean =
    relativePath?.contains("Screenshots/", ignoreCase = true) == true || bucket == "Screenshots"

package com.xerahs.android.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object FileNamePattern {

    fun resolve(pattern: String, originalFileName: String): String {
        val nameWithoutExt = originalFileName.substringBeforeLast('.')
        val extension = originalFileName.substringAfterLast('.', "png")
        val now = Date()

        val resolved = pattern
            .replace("{original}", nameWithoutExt)
            .replace("{date}", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now))
            .replace("{time}", SimpleDateFormat("HH-mm-ss", Locale.US).format(now))
            .replace("{timestamp}", System.currentTimeMillis().toString())
            .replace("{random}", UUID.randomUUID().toString().take(8))

        return "$resolved.$extension"
    }
}

object PathPattern {

    fun resolve(path: String): String {
        val now = Date()
        return path
            .replace("{yyyy}", SimpleDateFormat("yyyy", Locale.US).format(now))
            .replace("{yy}", SimpleDateFormat("yy", Locale.US).format(now))
            .replace("{MM}", SimpleDateFormat("MM", Locale.US).format(now))
            .replace("{dd}", SimpleDateFormat("dd", Locale.US).format(now))
            .replace("{month}", SimpleDateFormat("MMMM", Locale.US).format(now).lowercase())
    }
}

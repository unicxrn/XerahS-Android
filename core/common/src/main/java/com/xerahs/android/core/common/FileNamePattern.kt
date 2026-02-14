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

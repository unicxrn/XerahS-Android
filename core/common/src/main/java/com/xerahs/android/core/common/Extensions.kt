package com.xerahs.android.core.common

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

fun generateId(): String = UUID.randomUUID().toString()

fun generateTimestamp(): Long = System.currentTimeMillis()

fun Long.toFormattedDate(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toShortDate(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Bitmap.saveTo(file: File, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, quality: Int = 100): File {
    FileOutputStream(file).use { out ->
        compress(format, quality, out)
    }
    return file
}

fun Context.capturesDir(): File {
    val dir = File(filesDir, "captures")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun Context.exportsDir(): File {
    val dir = File(filesDir, "exports")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun String.fileExtension(): String = substringAfterLast('.', "png")

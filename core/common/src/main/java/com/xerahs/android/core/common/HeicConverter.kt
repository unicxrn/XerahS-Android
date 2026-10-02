package com.xerahs.android.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import java.io.File
import java.io.FileOutputStream

object HeicConverter {
    /**
     * Decodes a HEIC/HEIF file and writes it as PNG into the cache dir.
     * Returns null when unsupported (API < 28) or decoding fails — callers upload the original.
     */
    fun toPng(context: Context, source: File): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source))
            val dir = File(context.cacheDir, "heic_${System.currentTimeMillis()}").apply { mkdirs() }
            val out = File(dir, source.nameWithoutExtension + ".png")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            out
        } catch (e: Exception) {
            null
        }
    }
}

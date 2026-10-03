package com.xerahs.android.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

object HeicConverter {
    /** Cap decode output to ~24MP to avoid OOM on very large HEIC files. */
    private const val MAX_PIXELS = 24_000_000L

    /**
     * Decodes a HEIC/HEIF file and writes it as PNG into the cache dir.
     * Returns null when unsupported (API < 28) or decoding fails — callers upload the original.
     */
    fun toPng(context: Context, source: File): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                val pixels = info.size.width.toLong() * info.size.height.toLong()
                if (pixels > MAX_PIXELS) {
                    val scale = sqrt(MAX_PIXELS.toDouble() / pixels.toDouble())
                    val w = (info.size.width * scale).toInt().coerceAtLeast(1)
                    val h = (info.size.height * scale).toInt().coerceAtLeast(1)
                    decoder.setTargetSize(w, h)
                }
            }
            val dir = File(context.cacheDir, "heic_${System.currentTimeMillis()}").apply { mkdirs() }
            val out = File(dir, source.nameWithoutExtension + ".png")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            out
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}

package com.xerahs.android.feature.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.xerahs.android.core.common.image.BatchSizing
import com.xerahs.android.core.common.image.EffectSettings
import com.xerahs.android.core.common.image.ImageEffects
import com.xerahs.android.core.common.image.Watermark
import java.io.File

enum class OutputFormat(val label: String) { ORIGINAL("Original"), PNG("PNG"), JPEG("JPEG"), WEBP("WebP") }

data class BatchOptions(
    val maxDimension: Int = 0,
    val format: OutputFormat = OutputFormat.ORIGINAL,
    val quality: Int = 90,
    val watermark: Watermark? = null,
)

internal object BatchImageProcessor {
    // Processes one image into [outDir]; returns the written file.
    fun process(context: Context, uri: Uri, options: BatchOptions, outDir: File, index: Int): File {
        val sourceMime = context.contentResolver.getType(uri)
        val maxSide = if (options.maxDimension == 0) 8192 else options.maxDimension * 2
        val src = ImageLoading.load(context, uri, maxSide = maxSide)
            ?: throw IllegalArgumentException("Can't decode image")
        val (w, h) = BatchSizing.target(src.width, src.height, options.maxDimension)
        var bmp = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
        var bmpIsSource = bmp === src
        options.watermark?.takeIf { it.text.isNotBlank() }?.let {
            val watermarked = ImageEffects.apply(bmp, EffectSettings(watermark = it))
            if (!bmpIsSource) bmp.recycle()
            bmp = watermarked
            bmpIsSource = false
        }
        val format = when (options.format) {
            OutputFormat.ORIGINAL -> when (sourceMime) { "image/png" -> OutputFormat.PNG; "image/webp" -> OutputFormat.WEBP; else -> OutputFormat.JPEG }
            else -> options.format
        }
        val (compress, ext) = when (format) {
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG to "png"
            OutputFormat.WEBP -> (if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP) to "webp"
            else -> Bitmap.CompressFormat.JPEG to "jpg"
        }
        val out = File(outDir.apply { mkdirs() }, "xerahs_${System.currentTimeMillis()}_$index.$ext")
        out.outputStream().use { bmp.compress(compress, options.quality, it) }
        if (!bmpIsSource) bmp.recycle()
        return out
    }

    // Copies a processed file into Pictures/XerahS. Returns false below Android 10.
    fun saveToGallery(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val mime = when (file.extension) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/XerahS")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
        return true
    }
}

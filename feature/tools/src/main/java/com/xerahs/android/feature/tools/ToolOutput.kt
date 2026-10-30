package com.xerahs.android.feature.tools

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shared output steps for tools that render one image: write it to cache (which is also what the
 * Upload screen takes), share it, or save a copy to Pictures/XerahS.
 */
object ToolOutput {
    private const val OUT_DIR = "tools_out"

    /** Writes [bitmap] as PNG into cacheDir/tools_out and returns the file. Call off the main thread. */
    fun writeToCache(context: Context, bitmap: Bitmap, prefix: String): File {
        val dir = File(context.cacheDir, OUT_DIR).apply { mkdirs() }
        val out = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        try {
            out.outputStream().buffered().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Couldn't encode the image" }
            }
        } catch (t: Throwable) {
            out.delete()
            throw t
        }
        return out
    }

    /** Opens the share sheet for [file], which must live under cacheDir or one of the FileProvider paths. */
    fun share(context: Context, file: File, title: String? = null) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = mimeFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title)
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Copies [file] into Pictures/XerahS. Returns false below Android 10 or on failure; never
     * throws (storage-full and similar I/O errors are swallowed).
     */
    fun saveToPictures(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeFor(file))
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/XerahS")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                false
            } else {
                val copied = runCatching {
                    resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
                }.getOrDefault(false)
                if (copied) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                } else {
                    resolver.delete(uri, null, null)
                }
                copied
            }
        }.getOrDefault(false)
    }

    private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
}

package com.xerahs.android.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

internal object ImageLoading {
    // Decodes [uri] with the longer side downsampled to at most [maxSide], and applies EXIF
    // orientation so portrait camera photos aren't rotated.
    fun load(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap? {
        val (w, h) = rawSize(context, uri) ?: return null
        var sample = 1
        while (maxOf(w, h) / sample > maxSide) sample *= 2
        return decodeSampled(context, uri, sample)
    }

    // Size as displayed, after EXIF rotation (90 and 270 swap the sides).
    fun orientedSize(context: Context, uri: Uri): Pair<Int, Int>? {
        val (w, h) = rawSize(context, uri) ?: return null
        return if (swapsSides(context, uri)) h to w else w to h
    }

    // Decodes with the given power-of-two [sample] and applies EXIF orientation.
    fun decodeSampled(context: Context, uri: Uri, sample: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val matrix = orientationMatrix(context, uri)
        if (matrix == null || matrix.isIdentity) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun rawSize(context: Context, uri: Uri): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return bounds.outWidth to bounds.outHeight
    }

    private fun swapsSides(context: Context, uri: Uri): Boolean = when (readOrientation(context, uri)) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> true
        else -> false
    }

    private fun readOrientation(context: Context, uri: Uri): Int? = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull()

    private fun orientationMatrix(context: Context, uri: Uri): Matrix? {
        val orientation = readOrientation(context, uri) ?: return null
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return null
        }
        return matrix
    }
}

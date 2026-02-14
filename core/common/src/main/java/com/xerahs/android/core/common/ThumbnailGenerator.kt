package com.xerahs.android.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ThumbnailGenerator {

    private const val THUMBNAIL_SIZE = 200

    fun generate(context: Context, sourceFile: File): String? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)

            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, THUMBNAIL_SIZE)
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize

            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options) ?: return null

            val scale = THUMBNAIL_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val width = (bitmap.width * scale).toInt()
            val height = (bitmap.height * scale).toInt()
            val thumbnail = Bitmap.createScaledBitmap(bitmap, width, height, true)

            val thumbDir = File(context.filesDir, "thumbnails")
            if (!thumbDir.exists()) thumbDir.mkdirs()

            val thumbFile = File(thumbDir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(thumbFile).use { out ->
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            if (thumbnail != bitmap) thumbnail.recycle()
            bitmap.recycle()

            thumbFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, targetSize: Int): Int {
        var sampleSize = 1
        val maxDimension = maxOf(width, height)
        while (maxDimension / sampleSize > targetSize * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }
}

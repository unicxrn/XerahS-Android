package com.xerahs.android.feature.annotation.crop

import android.graphics.Bitmap
import android.graphics.Rect

object CropEngine {

    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val left = rect.left.coerceIn(0, bitmap.width)
        val top = rect.top.coerceIn(0, bitmap.height)
        val right = rect.right.coerceIn(left, bitmap.width)
        val bottom = rect.bottom.coerceIn(top, bitmap.height)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) return bitmap

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }
}

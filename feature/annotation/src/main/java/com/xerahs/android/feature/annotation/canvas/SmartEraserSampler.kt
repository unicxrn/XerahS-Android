package com.xerahs.android.feature.annotation.canvas

import android.graphics.Bitmap
import com.xerahs.android.core.common.image.EdgeColor

object SmartEraserSampler {
    // Average colour along the edge of the rect (bitmap coordinates), clamped to the bitmap.
    fun sample(bitmap: Bitmap, startX: Float, startY: Float, endX: Float, endY: Float): Int {
        val l = minOf(startX, endX).toInt().coerceIn(0, bitmap.width - 1)
        val t = minOf(startY, endY).toInt().coerceIn(0, bitmap.height - 1)
        val r = maxOf(startX, endX).toInt().coerceIn(l + 1, bitmap.width)
        val b = maxOf(startY, endY).toInt().coerceIn(t + 1, bitmap.height)
        val w = r - l; val h = b - t
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, l, t, w, h)
        return EdgeColor.average(pixels, w, h)
    }
}

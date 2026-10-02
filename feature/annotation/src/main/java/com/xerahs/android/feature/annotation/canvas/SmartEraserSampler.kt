package com.xerahs.android.feature.annotation.canvas

import android.graphics.Bitmap
import com.xerahs.android.core.common.image.EdgeColor

object SmartEraserSampler {
    // Average colour along the edge of the rect (bitmap coordinates), clamped to the bitmap.
    // Only the border pixels are read (top/bottom rows, left/right columns), never the whole rect.
    fun sample(bitmap: Bitmap, startX: Float, startY: Float, endX: Float, endY: Float): Int {
        val l = minOf(startX, endX).toInt().coerceIn(0, bitmap.width - 1)
        val t = minOf(startY, endY).toInt().coerceIn(0, bitmap.height - 1)
        val r = maxOf(startX, endX).toInt().coerceIn(l + 1, bitmap.width)
        val b = maxOf(startY, endY).toInt().coerceIn(t + 1, bitmap.height)
        val w = r - l; val h = b - t

        val topRow = IntArray(w)
        bitmap.getPixels(topRow, 0, w, l, t, w, 1)
        val bottomRow = IntArray(w)
        bitmap.getPixels(bottomRow, 0, w, l, b - 1, w, 1)

        val innerH = h - 2
        val leftCol = if (innerH > 0) IntArray(innerH) else IntArray(0)
        val rightCol = if (innerH > 0) IntArray(innerH) else IntArray(0)
        if (innerH > 0) {
            bitmap.getPixels(leftCol, 0, 1, l, t + 1, 1, innerH)
            bitmap.getPixels(rightCol, 0, 1, r - 1, t + 1, 1, innerH)
        }

        val combined = topRow + bottomRow + leftCol + rightCol
        return EdgeColor.average(combined, combined.size, 1)
    }
}

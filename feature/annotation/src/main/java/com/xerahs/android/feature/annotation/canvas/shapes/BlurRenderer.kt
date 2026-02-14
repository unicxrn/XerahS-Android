package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.xerahs.android.core.domain.model.Annotation

object BlurRenderer {

    fun draw(canvas: Canvas, blur: Annotation.Blur, sourceBitmap: Bitmap) {
        val left = minOf(blur.startX, blur.endX).toInt().coerceAtLeast(0)
        val top = minOf(blur.startY, blur.endY).toInt().coerceAtLeast(0)
        val right = maxOf(blur.startX, blur.endX).toInt().coerceAtMost(sourceBitmap.width)
        val bottom = maxOf(blur.startY, blur.endY).toInt().coerceAtMost(sourceBitmap.height)

        if (right <= left || bottom <= top) return

        val width = right - left
        val height = bottom - top

        // Pixelate fallback (works on all API levels)
        // Scale down then scale back up for pixelation effect
        val pixelSize = (blur.blurRadius / 3f).coerceAtLeast(2f).toInt()
        val smallWidth = (width / pixelSize).coerceAtLeast(1)
        val smallHeight = (height / pixelSize).coerceAtLeast(1)

        try {
            // Extract the region
            val region = Bitmap.createBitmap(sourceBitmap, left, top, width, height)

            // Scale down
            val small = Bitmap.createScaledBitmap(region, smallWidth, smallHeight, false)

            // Scale back up (pixelated)
            val pixelated = Bitmap.createScaledBitmap(small, width, height, false)

            canvas.drawBitmap(pixelated, left.toFloat(), top.toFloat(), Paint())

            // Clean up
            if (region !== sourceBitmap) region.recycle()
            small.recycle()
            pixelated.recycle()
        } catch (e: Exception) {
            // Fallback: draw a semi-transparent overlay
            val paint = Paint().apply {
                color = 0x80808080.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(
                left.toFloat(), top.toFloat(),
                right.toFloat(), bottom.toFloat(),
                paint
            )
        }
    }
}

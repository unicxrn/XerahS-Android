package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.xerahs.android.core.domain.model.Annotation

/**
 * Pixelate renderer. Mirrors BlurRenderer's downscale -> nearest-neighbor
 * upscale mosaic approach, driven by [Annotation.Pixelate.blockSize] instead
 * of a blur radius.
 */
object PixelateRenderer {

    fun draw(canvas: Canvas, pixelate: Annotation.Pixelate, sourceBitmap: Bitmap) {
        val left = minOf(pixelate.startX, pixelate.endX).toInt().coerceAtLeast(0)
        val top = minOf(pixelate.startY, pixelate.endY).toInt().coerceAtLeast(0)
        val right = maxOf(pixelate.startX, pixelate.endX).toInt().coerceAtMost(sourceBitmap.width)
        val bottom = maxOf(pixelate.startY, pixelate.endY).toInt().coerceAtMost(sourceBitmap.height)

        if (right <= left || bottom <= top) return

        val width = right - left
        val height = bottom - top

        // Block size controls the mosaic coarseness.
        val pixelSize = pixelate.blockSize.coerceAtLeast(2f).toInt()
        val smallWidth = (width / pixelSize).coerceAtLeast(1)
        val smallHeight = (height / pixelSize).coerceAtLeast(1)

        try {
            // Extract the region
            val region = Bitmap.createBitmap(sourceBitmap, left, top, width, height)

            // Scale down
            val small = Bitmap.createScaledBitmap(region, smallWidth, smallHeight, false)

            // Scale back up (nearest-neighbor -> blocky mosaic)
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
            paint.alpha = (paint.alpha * pixelate.opacity).toInt()
            canvas.drawRect(
                left.toFloat(), top.toFloat(),
                right.toFloat(), bottom.toFloat(),
                paint
            )
        }
    }
}

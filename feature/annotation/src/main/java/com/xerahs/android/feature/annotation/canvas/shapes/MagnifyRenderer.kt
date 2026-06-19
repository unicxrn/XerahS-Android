package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.xerahs.android.core.domain.model.Annotation

/**
 * Magnify (loupe) renderer. Samples a square of the source bitmap centered at
 * (centerX, centerY) and draws it, scaled up, into a circle of [radius] at the
 * same center. Mirrors BlurRenderer's signature: takes the source bitmap.
 */
object MagnifyRenderer {

    fun draw(canvas: Canvas, magnify: Annotation.Magnify, sourceBitmap: Bitmap) {
        val radius = magnify.radius
        if (radius <= 0f) return

        val zoom = magnify.zoom.coerceAtLeast(1f)

        // Source square side: a region of (2*radius)/zoom magnified to fill (2*radius).
        val half = (radius / zoom)
        var srcLeft = (magnify.centerX - half).toInt()
        var srcTop = (magnify.centerY - half).toInt()
        var srcRight = (magnify.centerX + half).toInt()
        var srcBottom = (magnify.centerY + half).toInt()

        // Clamp to bitmap bounds.
        srcLeft = srcLeft.coerceIn(0, sourceBitmap.width)
        srcTop = srcTop.coerceIn(0, sourceBitmap.height)
        srcRight = srcRight.coerceIn(0, sourceBitmap.width)
        srcBottom = srcBottom.coerceIn(0, sourceBitmap.height)

        val canDrawSample = srcRight > srcLeft && srcBottom > srcTop

        if (canDrawSample) {
            try {
                canvas.save()
                canvas.clipPath(
                    Path().apply { addCircle(magnify.centerX, magnify.centerY, radius, Path.Direction.CW) }
                )
                val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)
                val dstRect = RectF(
                    magnify.centerX - radius,
                    magnify.centerY - radius,
                    magnify.centerX + radius,
                    magnify.centerY + radius
                )
                val samplePaint = Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                }
                canvas.drawBitmap(sourceBitmap, srcRect, dstRect, samplePaint)
                canvas.restore()
            } catch (e: Exception) {
                // Fall through to the outline-only render below.
            }
        }

        // Circle border.
        val strokePaint = Paint().apply {
            color = magnify.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = magnify.strokeWidth
            isAntiAlias = true
        }
        strokePaint.alpha = (strokePaint.alpha * magnify.opacity.coerceIn(0f, 1f)).toInt()
        canvas.drawCircle(magnify.centerX, magnify.centerY, radius, strokePaint)
    }
}

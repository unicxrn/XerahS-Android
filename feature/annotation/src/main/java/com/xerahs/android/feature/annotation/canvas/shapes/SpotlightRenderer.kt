package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.xerahs.android.core.domain.model.Annotation

/**
 * Spotlight renderer. Dims the entire canvas except a focused region defined by
 * start..end. [Annotation.Spotlight.shape] 0 = ellipse, 1 = rect.
 *
 * Uses clipOutPath / clipOutRect (API 26+, minSdk 26) to punch the region out of
 * a full-canvas dim overlay.
 */
object SpotlightRenderer {

    fun draw(canvas: Canvas, spotlight: Annotation.Spotlight) {
        val bounds = Rect()
        if (!canvas.getClipBounds(bounds) || bounds.isEmpty) return

        val left = minOf(spotlight.startX, spotlight.endX)
        val top = minOf(spotlight.startY, spotlight.endY)
        val right = maxOf(spotlight.startX, spotlight.endX)
        val bottom = maxOf(spotlight.startY, spotlight.endY)

        val regionRect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        val regionRectF = RectF(left, top, right, bottom)

        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
            alpha = (spotlight.opacity.coerceIn(0f, 1f) * 255f).toInt()
        }

        canvas.save()
        if (spotlight.shape == 0) {
            canvas.clipOutPath(Path().apply { addOval(regionRectF, Path.Direction.CW) })
        } else {
            canvas.clipOutRect(regionRect)
        }
        canvas.drawRect(bounds, paint)
        canvas.restore()
    }
}

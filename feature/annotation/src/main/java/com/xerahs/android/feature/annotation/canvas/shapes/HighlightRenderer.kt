package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import com.xerahs.android.core.domain.model.Annotation

/**
 * Highlight renderer. Draws a filled translucent rectangle from start..end
 * using strokeColor at the annotation opacity, no border (highlighter look).
 */
object HighlightRenderer {

    fun draw(canvas: Canvas, highlight: Annotation.Highlight) {
        val left = minOf(highlight.startX, highlight.endX)
        val top = minOf(highlight.startY, highlight.endY)
        val right = maxOf(highlight.startX, highlight.endX)
        val bottom = maxOf(highlight.startY, highlight.endY)

        val fillPaint = Paint().apply {
            color = highlight.strokeColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        fillPaint.alpha = (fillPaint.alpha * highlight.opacity).toInt()

        canvas.drawRect(left, top, right, bottom, fillPaint)
    }
}

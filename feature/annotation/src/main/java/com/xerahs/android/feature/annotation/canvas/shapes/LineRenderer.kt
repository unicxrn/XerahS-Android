package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import com.xerahs.android.core.domain.model.Annotation

/**
 * Straight line renderer. Draws a stroked line from start to end.
 * Mirrors ArrowRenderer without the arrowhead polygon.
 */
object LineRenderer {

    fun draw(canvas: Canvas, line: Annotation.Line) {
        val paint = Paint().apply {
            color = line.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = line.strokeWidth
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        paint.alpha = (paint.alpha * line.opacity).toInt()

        canvas.drawLine(line.startX, line.startY, line.endX, line.endY, paint)
    }
}

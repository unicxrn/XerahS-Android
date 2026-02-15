package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import com.xerahs.android.core.domain.model.Annotation

object RectangleRenderer {

    fun draw(canvas: Canvas, rect: Annotation.Rectangle) {
        val left = minOf(rect.startX, rect.endX)
        val top = minOf(rect.startY, rect.endY)
        val right = maxOf(rect.startX, rect.endX)
        val bottom = maxOf(rect.startY, rect.endY)

        // Draw fill if present
        rect.fillColor?.let { fillColor ->
            val fillPaint = Paint().apply {
                color = fillColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            fillPaint.alpha = (fillPaint.alpha * rect.opacity).toInt()
            canvas.drawRect(left, top, right, bottom, fillPaint)
        }

        // Draw stroke
        val strokePaint = Paint().apply {
            color = rect.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = rect.strokeWidth
            isAntiAlias = true
            strokeJoin = Paint.Join.MITER
        }
        strokePaint.alpha = (strokePaint.alpha * rect.opacity).toInt()
        canvas.drawRect(left, top, right, bottom, strokePaint)
    }
}

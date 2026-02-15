package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import com.xerahs.android.core.domain.model.Annotation

object CircleRenderer {

    fun draw(canvas: Canvas, circle: Annotation.Circle) {
        // Draw fill if present
        circle.fillColor?.let { fillColor ->
            val fillPaint = Paint().apply {
                color = fillColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            fillPaint.alpha = (fillPaint.alpha * circle.opacity).toInt()
            canvas.drawCircle(circle.centerX, circle.centerY, circle.radius, fillPaint)
        }

        // Draw stroke
        val strokePaint = Paint().apply {
            color = circle.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = circle.strokeWidth
            isAntiAlias = true
            strokeJoin = Paint.Join.MITER
        }
        strokePaint.alpha = (strokePaint.alpha * circle.opacity).toInt()
        canvas.drawCircle(circle.centerX, circle.centerY, circle.radius, strokePaint)
    }
}

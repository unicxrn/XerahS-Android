package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.xerahs.android.core.domain.model.Annotation

object NumberedStepRenderer {

    fun draw(canvas: Canvas, step: Annotation.NumberedStep) {
        // Draw filled circle
        val fillPaint = Paint().apply {
            color = step.strokeColor
            style = Paint.Style.FILL
            isAntiAlias = true
            alpha = (alpha * step.opacity).toInt()
        }
        canvas.drawCircle(step.centerX, step.centerY, step.radius, fillPaint)

        // Draw number text centered in circle
        val textPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = step.radius * 1.2f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            alpha = (alpha * step.opacity).toInt()
        }

        val metrics = textPaint.fontMetrics
        val textY = step.centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(step.number.toString(), step.centerX, textY, textPaint)
    }
}

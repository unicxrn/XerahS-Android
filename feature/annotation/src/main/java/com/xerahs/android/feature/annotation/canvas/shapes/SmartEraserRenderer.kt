package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import com.xerahs.android.core.domain.model.Annotation

object SmartEraserRenderer {
    fun draw(canvas: Canvas, e: Annotation.SmartEraser) {
        canvas.drawRect(
            minOf(e.startX, e.endX), minOf(e.startY, e.endY), maxOf(e.startX, e.endX), maxOf(e.startY, e.endY),
            Paint().apply { color = e.fillColor; style = Paint.Style.FILL }
        )
    }
}

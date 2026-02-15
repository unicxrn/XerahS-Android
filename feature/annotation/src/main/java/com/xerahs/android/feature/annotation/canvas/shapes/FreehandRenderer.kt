package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.xerahs.android.core.domain.model.Annotation

object FreehandRenderer {

    fun draw(canvas: Canvas, freehand: Annotation.Freehand) {
        if (freehand.points.size < 2) return

        val path = Path().apply {
            val first = freehand.points.first()
            moveTo(first.first, first.second)
            for (i in 1 until freehand.points.size) {
                val point = freehand.points[i]
                lineTo(point.first, point.second)
            }
        }

        val paint = Paint().apply {
            color = freehand.strokeColor
            style = Paint.Style.STROKE
            strokeWidth = freehand.strokeWidth
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        paint.alpha = (paint.alpha * freehand.opacity).toInt()

        canvas.drawPath(path, paint)
    }
}

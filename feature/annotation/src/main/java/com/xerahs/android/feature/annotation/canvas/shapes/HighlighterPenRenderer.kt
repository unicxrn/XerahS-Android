package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.xerahs.android.core.domain.model.Annotation

object HighlighterPenRenderer {
    fun draw(canvas: Canvas, p: Annotation.HighlighterPen) {
        if (p.points.size < 2) return
        val path = Path().apply {
            moveTo(p.points[0].first, p.points[0].second)
            p.points.drop(1).forEach { (x, y) -> lineTo(x, y) }
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.strokeColor
            alpha = (p.opacity * 255).toInt()
            style = Paint.Style.STROKE
            strokeWidth = p.strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
    }
}

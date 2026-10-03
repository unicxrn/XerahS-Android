package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import com.xerahs.android.core.domain.model.Annotation

object SpeechBalloonRenderer {
    fun draw(canvas: Canvas, b: Annotation.SpeechBalloon) {
        val l = minOf(b.startX, b.endX); val t = minOf(b.startY, b.endY)
        val r = maxOf(b.startX, b.endX); val bottom = maxOf(b.startY, b.endY)
        val w = r - l; val h = bottom - t
        if (w < 4f || h < 4f) return
        val radius = minOf(w, h) * 0.2f
        val baseX = l + w * 0.2f
        val path = Path().apply {
            addRoundRect(RectF(l, t, r, bottom), radius, radius, Path.Direction.CW)
            moveTo(baseX, bottom - 1f)
            lineTo(b.tailX, b.tailY)
            lineTo(baseX + w * 0.15f, bottom - 1f)
            close()
        }
        val alpha = (b.opacity * 255).toInt()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = b.fillColor; this.alpha = alpha })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = b.strokeColor; style = Paint.Style.STROKE; strokeWidth = b.strokeWidth; this.alpha = alpha
        })
        val pad = minOf(w, h) * 0.12f
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = b.strokeColor; textSize = b.fontSize; this.alpha = alpha }
        val layout = StaticLayout.Builder.obtain(b.text, 0, b.text.length, textPaint, maxOf(1, (w - 2 * pad).toInt())).build()
        canvas.save()
        canvas.clipRect(l, t, r, bottom)
        canvas.translate(l + pad, t + pad)
        layout.draw(canvas)
        canvas.restore()
    }
}

package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.xerahs.android.core.domain.model.Annotation

object TextRenderer {

    fun draw(canvas: Canvas, textAnnotation: Annotation.Text) {
        val typeface = when {
            textAnnotation.isBold && textAnnotation.isItalic -> Typeface.defaultFromStyle(Typeface.BOLD_ITALIC)
            textAnnotation.isBold -> Typeface.defaultFromStyle(Typeface.BOLD)
            textAnnotation.isItalic -> Typeface.defaultFromStyle(Typeface.ITALIC)
            else -> Typeface.DEFAULT
        }

        val paint = Paint().apply {
            color = textAnnotation.strokeColor
            textSize = textAnnotation.fontSize
            this.typeface = typeface
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // Draw text with word wrap
        val lines = wrapText(textAnnotation.text, paint, canvas.width.toFloat() - textAnnotation.x)
        var y = textAnnotation.y + textAnnotation.fontSize // baseline offset
        val lineHeight = textAnnotation.fontSize * 1.2f
        val padding = 4f

        // Background paint
        val bgPaint = textAnnotation.backgroundColor?.let { bgColor ->
            Paint().apply {
                color = bgColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }

        for (line in lines) {
            if (bgPaint != null) {
                val textWidth = paint.measureText(line)
                val metrics = paint.fontMetrics
                val bgRect = RectF(
                    textAnnotation.x - padding,
                    y + metrics.ascent - padding,
                    textAnnotation.x + textWidth + padding,
                    y + metrics.descent + padding
                )
                canvas.drawRoundRect(bgRect, padding, padding, bgPaint)
            }
            canvas.drawText(line, textAnnotation.x, y, paint)
            y += lineHeight
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (maxWidth <= 0) return listOf(text)

        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines.ifEmpty { listOf(text) }
    }
}

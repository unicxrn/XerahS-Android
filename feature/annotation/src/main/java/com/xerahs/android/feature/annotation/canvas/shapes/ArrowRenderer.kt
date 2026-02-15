package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.xerahs.android.core.domain.model.Annotation
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Arrow renderer ported from XerahS AnnotationGeometryHelper.cs.
 * Uses a 6-point polygon: shaft (2 points) + arrowhead base (2 points) + tip (1 point),
 * mirrored on both sides of the arrow axis.
 */
object ArrowRenderer {

    // Arrow head angle ~35 degrees (Math.PI / 5.14 from XerahS)
    private val ARROW_ANGLE = Math.PI / 5.14
    // Head size enlargement factor (from XerahS)
    private const val HEAD_ENLARGE = 1.5f
    // Shaft width at arrow base as proportion of enlarged head size
    private const val SHAFT_END_WIDTH_RATIO = 0.30f
    // Arrow head width multiplier relative to stroke width
    private const val WIDTH_MULTIPLIER = 3.0f

    fun draw(canvas: Canvas, arrow: Annotation.Arrow) {
        val dx = arrow.endX - arrow.startX
        val dy = arrow.endY - arrow.startY
        val length = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (length < 1f) return

        // Unit direction vector
        val ux = dx / length
        val uy = dy / length

        // Perpendicular vector
        val perpX = -uy
        val perpY = ux

        val headSize = arrow.arrowHeadSize
        val enlargedHeadSize = headSize * HEAD_ENLARGE
        val shaftHalfWidth = arrow.strokeWidth * WIDTH_MULTIPLIER / 2f

        // Arrowhead base point (on the line, behind the tip)
        val arrowBaseX = arrow.endX - enlargedHeadSize * ux
        val arrowBaseY = arrow.endY - enlargedHeadSize * uy

        // Arrowhead wing width at the base
        val headWingWidth = (enlargedHeadSize * tan(ARROW_ANGLE)).toFloat()

        // Shaft width at arrow base transition
        val shaftEndWidth = enlargedHeadSize * SHAFT_END_WIDTH_RATIO

        // Build 6-point polygon path
        val path = Path().apply {
            // 1. Start left (shaft start, left side)
            moveTo(
                arrow.startX + perpX * shaftHalfWidth,
                arrow.startY + perpY * shaftHalfWidth
            )
            // 2. Shaft end left (where shaft meets arrowhead base)
            lineTo(
                arrowBaseX + perpX * shaftEndWidth,
                arrowBaseY + perpY * shaftEndWidth
            )
            // 3. Arrow base left (wide wing)
            lineTo(
                arrowBaseX + perpX * headWingWidth,
                arrowBaseY + perpY * headWingWidth
            )
            // 4. Arrow tip
            lineTo(arrow.endX, arrow.endY)
            // 5. Arrow base right (wide wing)
            lineTo(
                arrowBaseX - perpX * headWingWidth,
                arrowBaseY - perpY * headWingWidth
            )
            // 6. Shaft end right
            lineTo(
                arrowBaseX - perpX * shaftEndWidth,
                arrowBaseY - perpY * shaftEndWidth
            )
            // 7. Start right (shaft start, right side)
            lineTo(
                arrow.startX - perpX * shaftHalfWidth,
                arrow.startY - perpY * shaftHalfWidth
            )
            close()
        }

        val fillPaint = Paint().apply {
            color = arrow.strokeColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        fillPaint.alpha = (fillPaint.alpha * arrow.opacity).toInt()

        canvas.drawPath(path, fillPaint)
    }
}

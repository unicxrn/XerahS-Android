package com.xerahs.android.feature.annotation.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY, NONE }

/** The crop frame as fractions (0..1) of the image, so it holds across rotation and resizes. */
data class CropFrame(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    /** The frame in pixels of a [width] x [height] image. */
    fun toRect(width: Int, height: Int): android.graphics.Rect = android.graphics.Rect(
        (left * width).roundToInt().coerceIn(0, width),
        (top * height).roundToInt().coerceIn(0, height),
        (right * width).roundToInt().coerceIn(0, width),
        (bottom * height).roundToInt().coerceIn(0, height),
    )
}

/**
 * Stateless crop frame. It must be laid out over exactly the image's bounds; [frame] is the single
 * source of truth and every drag reports a new one through [onFrameChange].
 */
@Composable
fun CropOverlay(
    frame: CropFrame,
    onFrameChange: (CropFrame) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentFrame by rememberUpdatedState(frame)
    val currentOnChange by rememberUpdatedState(onFrameChange)
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }
    val cropLeft = frame.left
    val cropTop = frame.top
    val cropRight = frame.right
    val cropBottom = frame.bottom

    val handleZone = with(LocalDensity.current) { 48.dp.toPx() }
    fun cornerZone(fx: Float, fy: Float): (LayoutCoordinates) -> Rect = { c ->
        val x = fx * c.size.width
        val y = fy * c.size.height
        Rect(x - handleZone / 2, y - handleZone / 2, x + handleZone / 2, y + handleZone / 2)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Corners often sit on the screen edge, where the system back gesture would take the drag.
            .systemGestureExclusion(cornerZone(cropLeft, cropTop))
            .systemGestureExclusion(cornerZone(cropRight, cropTop))
            .systemGestureExclusion(cornerZone(cropLeft, cropBottom))
            .systemGestureExclusion(cornerZone(cropRight, cropBottom))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val f = currentFrame
                        val nx = offset.x / size.width.toFloat()
                        val ny = offset.y / size.height.toFloat()
                        val threshold = 0.05f
                        fun near(a: Float, b: Float) = abs(a - b) < threshold
                        activeHandle = when {
                            near(nx, f.left) && near(ny, f.top) -> DragHandle.TOP_LEFT
                            near(nx, f.right) && near(ny, f.top) -> DragHandle.TOP_RIGHT
                            near(nx, f.left) && near(ny, f.bottom) -> DragHandle.BOTTOM_LEFT
                            near(nx, f.right) && near(ny, f.bottom) -> DragHandle.BOTTOM_RIGHT
                            nx in f.left..f.right && ny in f.top..f.bottom -> DragHandle.BODY
                            else -> DragHandle.NONE
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x / size.width.toFloat()
                        val dy = dragAmount.y / size.height.toFloat()
                        val f = currentFrame
                        val next = when (activeHandle) {
                            DragHandle.TOP_LEFT -> f.copy(
                                left = (f.left + dx).coerceIn(0f, f.right - MIN_SPAN),
                                top = (f.top + dy).coerceIn(0f, f.bottom - MIN_SPAN))
                            DragHandle.TOP_RIGHT -> f.copy(
                                right = (f.right + dx).coerceIn(f.left + MIN_SPAN, 1f),
                                top = (f.top + dy).coerceIn(0f, f.bottom - MIN_SPAN))
                            DragHandle.BOTTOM_LEFT -> f.copy(
                                left = (f.left + dx).coerceIn(0f, f.right - MIN_SPAN),
                                bottom = (f.bottom + dy).coerceIn(f.top + MIN_SPAN, 1f))
                            DragHandle.BOTTOM_RIGHT -> f.copy(
                                right = (f.right + dx).coerceIn(f.left + MIN_SPAN, 1f),
                                bottom = (f.bottom + dy).coerceIn(f.top + MIN_SPAN, 1f))
                            DragHandle.BODY -> {
                                val w = f.right - f.left
                                val h = f.bottom - f.top
                                val l = (f.left + dx).coerceIn(0f, 1f - w)
                                val t = (f.top + dy).coerceIn(0f, 1f - h)
                                CropFrame(l, t, l + w, t + h)
                            }
                            DragHandle.NONE -> f
                        }
                        if (next != f) currentOnChange(next)
                    },
                    onDragEnd = { activeHandle = DragHandle.NONE },
                    onDragCancel = { activeHandle = DragHandle.NONE }
                )
            }
    ) {
        val w = size.width
        val h = size.height

        val left = cropLeft * w
        val top = cropTop * h
        val right = cropRight * w
        val bottom = cropBottom * h

        // Dark mask outside crop
        val maskColor = Color.Black.copy(alpha = 0.5f)
        // Top
        drawRect(maskColor, Offset.Zero, Size(w, top))
        // Bottom
        drawRect(maskColor, Offset(0f, bottom), Size(w, h - bottom))
        // Left
        drawRect(maskColor, Offset(0f, top), Size(left, bottom - top))
        // Right
        drawRect(maskColor, Offset(right, top), Size(w - right, bottom - top))

        // Crop border
        drawRect(
            Color.White,
            Offset(left, top),
            Size(right - left, bottom - top),
            style = Stroke(width = 2f)
        )

        // Grid lines (rule of thirds)
        val thirdW = (right - left) / 3f
        val thirdH = (bottom - top) / 3f
        val gridColor = Color.White.copy(alpha = 0.4f)
        for (i in 1..2) {
            drawLine(gridColor, Offset(left + thirdW * i, top), Offset(left + thirdW * i, bottom), strokeWidth = 1f)
            drawLine(gridColor, Offset(left, top + thirdH * i), Offset(right, top + thirdH * i), strokeWidth = 1f)
        }

        // Corner handles
        val handleSize = 16f
        val handleColor = Color.White
        val corners = listOf(
            Offset(left, top), Offset(right, top),
            Offset(left, bottom), Offset(right, bottom)
        )
        corners.forEach { corner ->
            drawCircle(handleColor, handleSize / 2f, corner)
        }
    }
}

private const val MIN_SPAN = 0.05f

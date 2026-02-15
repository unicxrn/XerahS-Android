package com.xerahs.android.feature.annotation.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

private enum class DragHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY, NONE }

@Composable
fun CropOverlay(
    imageWidth: Int,
    imageHeight: Int,
    onCropRectChanged: (android.graphics.Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    var cropLeft by remember { mutableFloatStateOf(0.1f) }
    var cropTop by remember { mutableFloatStateOf(0.1f) }
    var cropRight by remember { mutableFloatStateOf(0.9f) }
    var cropBottom by remember { mutableFloatStateOf(0.9f) }
    var activeHandle by remember { mutableFloatStateOf(-1f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val nx = offset.x / w
                        val ny = offset.y / h
                        val threshold = 0.05f

                        activeHandle = when {
                            kotlin.math.abs(nx - cropLeft) < threshold && kotlin.math.abs(ny - cropTop) < threshold -> 0f
                            kotlin.math.abs(nx - cropRight) < threshold && kotlin.math.abs(ny - cropTop) < threshold -> 1f
                            kotlin.math.abs(nx - cropLeft) < threshold && kotlin.math.abs(ny - cropBottom) < threshold -> 2f
                            kotlin.math.abs(nx - cropRight) < threshold && kotlin.math.abs(ny - cropBottom) < threshold -> 3f
                            nx in cropLeft..cropRight && ny in cropTop..cropBottom -> 4f
                            else -> -1f
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x / size.width.toFloat()
                        val dy = dragAmount.y / size.height.toFloat()

                        when (activeHandle.toInt()) {
                            0 -> { cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f); cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f) }
                            1 -> { cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f); cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f) }
                            2 -> { cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f); cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f) }
                            3 -> { cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f); cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f) }
                            4 -> {
                                val w = cropRight - cropLeft
                                val h = cropBottom - cropTop
                                val newLeft = (cropLeft + dx).coerceIn(0f, 1f - w)
                                val newTop = (cropTop + dy).coerceIn(0f, 1f - h)
                                cropLeft = newLeft; cropRight = newLeft + w
                                cropTop = newTop; cropBottom = newTop + h
                            }
                        }

                        onCropRectChanged(android.graphics.Rect(
                            (cropLeft * imageWidth).toInt(),
                            (cropTop * imageHeight).toInt(),
                            (cropRight * imageWidth).toInt(),
                            (cropBottom * imageHeight).toInt()
                        ))
                    },
                    onDragEnd = { activeHandle = -1f }
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

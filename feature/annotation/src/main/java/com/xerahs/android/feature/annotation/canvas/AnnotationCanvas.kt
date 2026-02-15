package com.xerahs.android.feature.annotation.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.xerahs.android.core.domain.model.Annotation
import com.xerahs.android.feature.annotation.canvas.shapes.ArrowRenderer
import com.xerahs.android.feature.annotation.canvas.shapes.BlurRenderer
import com.xerahs.android.feature.annotation.canvas.shapes.CircleRenderer
import com.xerahs.android.feature.annotation.canvas.shapes.FreehandRenderer
import com.xerahs.android.feature.annotation.canvas.shapes.NumberedStepRenderer
import com.xerahs.android.feature.annotation.canvas.shapes.RectangleRenderer
import com.xerahs.android.feature.annotation.canvas.shapes.TextRenderer

@Composable
fun AnnotationCanvas(
    bitmap: Bitmap,
    annotations: List<Annotation>,
    currentAnnotation: Annotation?,
    onDragStart: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    selectedAnnotationId: String? = null,
    onAnnotationTapped: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Zoom/pan state
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    // Fit-to-canvas scale (computed during draw)
    var fitScale by remember { mutableFloatStateOf(1f) }
    var fitOffsetX by remember { mutableFloatStateOf(0f) }
    var fitOffsetY by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    var isDrawing = true
                    var hasMoved = false
                    var lastPosition = firstDown.position

                    // Convert to image coordinates
                    fun toImageCoords(pos: Offset): Offset {
                        val totalScale = fitScale * zoomLevel
                        val finalOffsetX = fitOffsetX + panX
                        val finalOffsetY = fitOffsetY + panY
                        val imgX = (pos.x - finalOffsetX) / totalScale
                        val imgY = (pos.y - finalOffsetY) / totalScale
                        return Offset(imgX, imgY)
                    }

                    onDragStart(toImageCoords(firstDown.position))

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { !it.changedToUp() }

                        if (pointers.size >= 2) {
                            // Two-finger gesture: zoom/pan
                            if (isDrawing && !hasMoved) {
                                // Haven't committed to drawing yet, switch to zoom/pan
                                isDrawing = false
                            }

                            if (!isDrawing) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()

                                zoomLevel = (zoomLevel * zoom).coerceIn(1f, 5f)
                                panX += pan.x
                                panY += pan.y

                                // Clamp pan
                                val bw = bitmap.width * fitScale * zoomLevel
                                val bh = bitmap.height * fitScale * zoomLevel
                                panX = panX.coerceIn(-(bw / 2), bw / 2)
                                panY = panY.coerceIn(-(bh / 2), bh / 2)
                            }

                            event.changes.forEach { it.consume() }
                        } else if (pointers.size == 1) {
                            val change = pointers[0]
                            if (isDrawing) {
                                hasMoved = true
                                change.consume()
                                lastPosition = change.position
                                onDrag(toImageCoords(change.position))
                            }
                        }

                        // All fingers lifted
                        if (event.changes.all { it.changedToUp() }) {
                            if (isDrawing) {
                                onDragEnd(toImageCoords(lastPosition))
                            }
                            break
                        }
                    }
                }
            }
    ) {
        // Calculate fit-to-canvas scaling
        val canvasWidth = size.width
        val canvasHeight = size.height
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        val scale = minOf(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
        fitScale = scale
        fitOffsetX = (canvasWidth - bitmapWidth * scale) / 2f
        fitOffsetY = (canvasHeight - bitmapHeight * scale) / 2f

        val totalScale = fitScale * zoomLevel
        val finalOffsetX = fitOffsetX + panX
        val finalOffsetY = fitOffsetY + panY

        // Draw background bitmap
        drawContext.canvas.nativeCanvas.apply {
            save()
            translate(finalOffsetX, finalOffsetY)
            scale(totalScale, totalScale)
            drawBitmap(bitmap, 0f, 0f, null)
            restore()
        }

        // Draw existing annotations
        drawContext.canvas.nativeCanvas.apply {
            save()
            translate(finalOffsetX, finalOffsetY)
            scale(totalScale, totalScale)

            val sortedAnnotations = annotations.sortedBy { it.zIndex }
            for (annotation in sortedAnnotations) {
                drawAnnotation(this, annotation, bitmap)
            }

            // Draw current in-progress annotation
            currentAnnotation?.let {
                drawAnnotation(this, it, bitmap)
            }

            restore()
        }
    }
}

private fun drawAnnotation(canvas: android.graphics.Canvas, annotation: Annotation, bitmap: Bitmap) {
    when (annotation) {
        is Annotation.Rectangle -> RectangleRenderer.draw(canvas, annotation)
        is Annotation.Arrow -> ArrowRenderer.draw(canvas, annotation)
        is Annotation.Text -> TextRenderer.draw(canvas, annotation)
        is Annotation.Blur -> BlurRenderer.draw(canvas, annotation, bitmap)
        is Annotation.Circle -> CircleRenderer.draw(canvas, annotation)
        is Annotation.Freehand -> FreehandRenderer.draw(canvas, annotation)
        is Annotation.NumberedStep -> NumberedStepRenderer.draw(canvas, annotation)
    }
}

private fun hitTest(annotations: List<Annotation>, x: Float, y: Float): String? {
    // Check in reverse z-order (topmost first)
    for (annotation in annotations.sortedByDescending { it.zIndex }) {
        val hit = when (annotation) {
            is Annotation.Rectangle -> {
                val l = minOf(annotation.startX, annotation.endX)
                val t = minOf(annotation.startY, annotation.endY)
                val r = maxOf(annotation.startX, annotation.endX)
                val b = maxOf(annotation.startY, annotation.endY)
                x in l..r && y in t..b
            }
            is Annotation.Arrow -> {
                val l = minOf(annotation.startX, annotation.endX) - 10f
                val t = minOf(annotation.startY, annotation.endY) - 10f
                val r = maxOf(annotation.startX, annotation.endX) + 10f
                val b = maxOf(annotation.startY, annotation.endY) + 10f
                x in l..r && y in t..b
            }
            is Annotation.Circle -> {
                val dx = x - annotation.centerX
                val dy = y - annotation.centerY
                dx * dx + dy * dy <= annotation.radius * annotation.radius
            }
            is Annotation.NumberedStep -> {
                val dx = x - annotation.centerX
                val dy = y - annotation.centerY
                dx * dx + dy * dy <= annotation.radius * annotation.radius
            }
            is Annotation.Text -> {
                x >= annotation.x && x <= annotation.x + 200f &&
                    y >= annotation.y && y <= annotation.y + annotation.fontSize * 1.5f
            }
            is Annotation.Blur -> {
                val l = minOf(annotation.startX, annotation.endX)
                val t = minOf(annotation.startY, annotation.endY)
                val r = maxOf(annotation.startX, annotation.endX)
                val b = maxOf(annotation.startY, annotation.endY)
                x in l..r && y in t..b
            }
            is Annotation.Freehand -> {
                annotation.points.any { (px, py) ->
                    val dx = x - px; val dy = y - py
                    dx * dx + dy * dy < 400f // ~20px radius
                }
            }
        }
        if (hit) return annotation.id
    }
    return null
}

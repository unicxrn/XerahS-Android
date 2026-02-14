package com.xerahs.android.feature.annotation.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.xerahs.android.core.domain.model.Annotation
import com.xerahs.android.feature.annotation.canvas.shapes.ArrowRenderer
import com.xerahs.android.feature.annotation.canvas.shapes.BlurRenderer
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
    modifier: Modifier = Modifier
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    // Calculate scale to fit bitmap in canvas
    var scaleX by remember { mutableStateOf(1f) }
    var scaleY by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Convert canvas coordinates to image coordinates
                        val imgX = (offset.x - offsetX) / scaleX
                        val imgY = (offset.y - offsetY) / scaleY
                        dragStart = Offset(imgX, imgY)
                        dragCurrent = Offset(imgX, imgY)
                        onDragStart(Offset(imgX, imgY))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val imgX = (change.position.x - offsetX) / scaleX
                        val imgY = (change.position.y - offsetY) / scaleY
                        dragCurrent = Offset(imgX, imgY)
                        onDrag(Offset(imgX, imgY))
                    },
                    onDragEnd = {
                        dragCurrent?.let { current ->
                            onDragEnd(current)
                        }
                        dragStart = null
                        dragCurrent = null
                    },
                    onDragCancel = {
                        dragStart = null
                        dragCurrent = null
                    }
                )
            }
    ) {
        // Calculate fit-to-canvas scaling
        val canvasWidth = size.width
        val canvasHeight = size.height
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        val scale = minOf(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
        scaleX = scale
        scaleY = scale
        offsetX = (canvasWidth - bitmapWidth * scale) / 2f
        offsetY = (canvasHeight - bitmapHeight * scale) / 2f

        // Draw background bitmap
        drawContext.canvas.nativeCanvas.apply {
            save()
            translate(offsetX, offsetY)
            scale(scaleX, scaleY)
            drawBitmap(bitmap, 0f, 0f, null)
            restore()
        }

        // Draw existing annotations
        drawContext.canvas.nativeCanvas.apply {
            save()
            translate(offsetX, offsetY)
            scale(scaleX, scaleY)

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
    }
}

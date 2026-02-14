package com.xerahs.android.feature.annotation.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import com.xerahs.android.core.domain.model.Annotation
import com.xerahs.android.feature.annotation.canvas.shapes.ArrowRenderer
import com.xerahs.android.feature.annotation.canvas.shapes.BlurRenderer
import com.xerahs.android.feature.annotation.canvas.shapes.RectangleRenderer
import com.xerahs.android.feature.annotation.canvas.shapes.TextRenderer
import java.io.File
import java.io.FileOutputStream

object AnnotationEngine {

    fun renderAnnotations(
        sourceBitmap: Bitmap,
        annotations: List<Annotation>
    ): Bitmap {
        val result = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val sortedAnnotations = annotations.sortedBy { it.zIndex }
        for (annotation in sortedAnnotations) {
            when (annotation) {
                is Annotation.Rectangle -> RectangleRenderer.draw(canvas, annotation)
                is Annotation.Arrow -> ArrowRenderer.draw(canvas, annotation)
                is Annotation.Text -> TextRenderer.draw(canvas, annotation)
                is Annotation.Blur -> BlurRenderer.draw(canvas, annotation, sourceBitmap)
            }
        }

        return result
    }

    fun exportToFile(
        bitmap: Bitmap,
        outputFile: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ): File {
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(format, quality, out)
        }
        return outputFile
    }
}

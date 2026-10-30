package com.xerahs.android.feature.tools.combine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.xerahs.android.feature.tools.ImageLoading
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.ensureActive

internal object CombineRenderer {
    const val PREVIEW_EDGE = 1600
    const val FULL_EDGE = 8000
    const val LOW_RAM_EDGE = 4096

    /** Long-edge cap for the full render: lower on low-RAM devices. */
    fun fullEdge(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return if (am?.isLowRamDevice == true) LOW_RAM_EDGE else FULL_EDGE
    }

    /** Output pixel cap: 64 MP, or 40% of the heap at 4 bytes a pixel if that is smaller. */
    fun maxPixels(): Long = min(MAX_INPUT_PIXELS, (Runtime.getRuntime().maxMemory() * 0.4 / 4).toLong())

    /**
     * Draws [files] into one bitmap using [layout]. Inputs are decoded one at a time with
     * power-of-two sampling, within a share of the pixel budget, so peak memory is the output plus
     * one input. Throws on unreadable input; may throw OutOfMemoryError as a last resort.
     */
    suspend fun render(
        context: Context,
        files: List<File>,
        options: CombineOptions,
        maxEdge: Int,
        maxPixels: Long = maxPixels(),
    ): Bitmap {
        require(files.isNotEmpty())
        val uris = files.map { Uri.fromFile(it) }
        val sizes = uris.map { ImageLoading.orientedSize(context, it) ?: throw IllegalArgumentException("Can't read an image") }
        val result = layout(sizes, options, maxEdge, maxPixels)
        val share = min(MAX_INPUT_PIXELS, maxPixels) / files.size
        val out = Bitmap.createBitmap(result.width.coerceAtLeast(1), result.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(out)
            canvas.drawColor(options.background)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            uris.forEachIndexed { i, uri ->
                coroutineContext.ensureActive()
                val r = result.rects[i]
                if (r.width <= 0 || r.height <= 0) return@forEachIndexed
                val (w, h) = sizes[i]
                // Never force a decode below the size it is drawn at; the layout already fits that in memory.
                val budget = max(share, r.width.toLong() * r.height)
                val sample = sampleSizeFor(w, h, r.width, r.height, budget)
                val src = ImageLoading.decodeSampled(context, uri, sample) ?: throw IllegalArgumentException("Can't read an image")
                try {
                    canvas.drawBitmap(src, null, Rect(r.left, r.top, r.right, r.bottom), paint)
                } finally {
                    src.recycle()
                }
            }
        } catch (t: Throwable) {
            out.recycle()
            throw t
        }
        return out
    }
}

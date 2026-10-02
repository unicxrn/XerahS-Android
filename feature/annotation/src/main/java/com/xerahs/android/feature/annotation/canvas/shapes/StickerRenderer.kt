package com.xerahs.android.feature.annotation.canvas.shapes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import com.xerahs.android.core.domain.model.Annotation

object StickerRenderer {
    private val cache = LruCache<String, Bitmap>(8)

    private fun load(path: String): Bitmap? = cache.get(path) ?: BitmapFactory.decodeFile(path)?.also { cache.put(path, it) }

    // Draws the sticker fit-center inside its box, keeping the aspect ratio.
    fun draw(canvas: Canvas, s: Annotation.Sticker) {
        val bmp = load(s.imagePath) ?: return
        val l = minOf(s.startX, s.endX); val t = minOf(s.startY, s.endY)
        val w = maxOf(s.startX, s.endX) - l; val h = maxOf(s.startY, s.endY) - t
        if (w < 2f || h < 2f) return
        val scale = minOf(w / bmp.width, h / bmp.height)
        val dw = bmp.width * scale; val dh = bmp.height * scale
        val dst = RectF(l + (w - dw) / 2, t + (h - dh) / 2, l + (w + dw) / 2, t + (h + dh) / 2)
        canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (s.opacity * 255).toInt() })
    }
}

package com.xerahs.android.core.common.image

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

object ImageEffects {
    fun needsAlpha(s: EffectSettings) = s.cornerRadius > 0 || s.shadow

    fun applyColor(src: Bitmap, s: EffectSettings): Bitmap {
        if (!s.hasColorChange) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(ColorMatrices.forSettings(s))) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    // Order: colour, rounded corners, border, shadow, watermark. Never recycles [src].
    fun apply(src: Bitmap, s: EffectSettings): Bitmap {
        var bmp = applyColor(src, s)
        if (s.cornerRadius > 0) bmp = roundCorners(bmp, s.cornerRadius.toFloat())
        if (s.borderWidth > 0) bmp = addBorder(bmp, s.borderWidth, s.borderColor, s.cornerRadius.toFloat())
        if (s.shadow) bmp = addShadow(bmp)
        s.watermark?.takeIf { it.text.isNotBlank() }?.let { bmp = drawWatermark(bmp, it) }
        return bmp
    }

    private fun roundCorners(src: Bitmap, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, paint)
        return out
    }

    private fun addBorder(src: Bitmap, width: Int, color: Int, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width + 2 * width, src.height + 2 * width, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val outer = if (radius > 0) radius + width else 0f
        canvas.drawRoundRect(RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), outer, outer, paint)
        canvas.drawBitmap(src, width.toFloat(), width.toFloat(), null)
        return out
    }

    private fun addShadow(src: Bitmap): Bitmap {
        val pad = maxOf(8, minOf(src.width, src.height) / 40)
        val out = Bitmap.createBitmap(src.width + 2 * pad, src.height + 2 * pad, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val alpha = src.extractAlpha()
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            this.alpha = 0x66
            maskFilter = BlurMaskFilter(pad * 0.6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(alpha, pad.toFloat(), pad.toFloat() + pad * 0.3f, shadowPaint)
        canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), null)
        alpha.recycle()
        return out
    }

    private fun drawWatermark(src: Bitmap, wm: Watermark): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val size = (minOf(out.width, out.height) * wm.sizeFraction).coerceAtLeast(12f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size
            alpha = (wm.opacity.coerceIn(0f, 1f) * 255).toInt()
            setShadowLayer(size * 0.08f, 0f, size * 0.04f, 0x99000000.toInt())
        }
        val textW = paint.measureText(wm.text)
        val fm = paint.fontMetrics
        val textH = fm.descent - fm.ascent
        val (x, top) = WatermarkLayout.position(out.width, out.height, textW, textH, wm.corner, size * 0.6f)
        canvas.drawText(wm.text, x, top - fm.ascent, paint)
        return out
    }
}

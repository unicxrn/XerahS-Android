package com.xerahs.android.feature.tools.beautify

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class BeautifyOptions(
    val paddingPct: Float = 0.08f,
    val background: Background = Background.Gradient(0),
    val cornerRadiusPct: Float = 0.03f,
    val shadow: Boolean = true,
    val aspect: Aspect = Aspect.AUTO,
)

sealed interface Background {
    data class Gradient(val preset: Int) : Background
    data class Solid(val argb: Int) : Background
    data object Accent : Background
    data object Transparent : Background
}

enum class Aspect { AUTO, SQUARE, PORTRAIT_4_5, WIDE_16_9 }

/** Diagonal gradient presets (top-left to bottom-right) with their chip labels. */
internal val GRADIENT_PRESETS: List<Triple<String, Int, Int>> = listOf(
    Triple("Lime", 0xFFC6F54A.toInt(), 0xFF4FE3B2.toInt()),
    Triple("Violet", 0xFF6D3BF0.toInt(), 0xFFFF5C8A.toInt()),
    Triple("Ocean", 0xFF3B82F6.toInt(), 0xFF2BE0E0.toInt()),
    Triple("Sunset", 0xFFFFB020.toInt(), 0xFFFF5C8A.toInt()),
    Triple("Ink", 0xFF1C1C26.toInt(), 0xFF09090C.toInt()),
    Triple("Paper", 0xFFF5F5F8.toInt(), 0xFFDCDCE6.toInt()),
)

/**
 * Canvas size for a [srcW] x [srcH] image: padding is [BeautifyOptions.paddingPct] of the long
 * edge on each side, then the canvas grows (never crops) to the chosen aspect. The result is
 * scaled down, keeping its ratio, so the long edge is at most [maxEdge].
 */
fun outputSize(srcW: Int, srcH: Int, o: BeautifyOptions, maxEdge: Int = 4096): Pair<Int, Int> {
    val w0 = srcW.coerceAtLeast(1)
    val h0 = srcH.coerceAtLeast(1)
    val pad = (o.paddingPct.coerceIn(0f, 0.5f) * max(w0, h0)).roundToInt()
    val iw = w0 + 2 * pad
    val ih = h0 + 2 * pad
    val (w, h) = when (o.aspect) {
        Aspect.AUTO -> iw to ih
        Aspect.SQUARE -> max(iw, ih) to max(iw, ih)
        Aspect.PORTRAIT_4_5 -> fitRatio(iw, ih, 4, 5)
        Aspect.WIDE_16_9 -> fitRatio(iw, ih, 16, 9)
    }
    val long = max(w, h)
    if (long <= maxEdge) return w to h
    val s = maxEdge.toDouble() / long
    return if (w >= h) maxEdge to max(1, (h * s).roundToInt()) else max(1, (w * s).roundToInt()) to maxEdge
}

// Smallest rw:rh box that contains iw x ih.
private fun fitRatio(iw: Int, ih: Int, rw: Int, rh: Int): Pair<Int, Int> =
    if (iw.toLong() * rh >= ih.toLong() * rw) iw to ceil(iw.toDouble() * rh / rw).toInt()
    else ceil(ih.toDouble() * rw / rh).toInt() to ih

internal data class ImagePlacement(val left: Float, val top: Float, val right: Float, val bottom: Float, val radius: Float)

/** Where the source lands inside an [outW] x [outH] canvas, scaled like the canvas was. */
internal fun placement(srcW: Int, srcH: Int, o: BeautifyOptions, outW: Int, outH: Int): ImagePlacement {
    val uncapped = outputSize(srcW, srcH, o, Int.MAX_VALUE)
    val s = outW.toFloat() / uncapped.first
    val iw = srcW * s
    val ih = srcH * s
    val left = (outW - iw) / 2f
    val top = (outH - ih) / 2f
    val radius = (o.cornerRadiusPct.coerceAtLeast(0f) * max(iw, ih)).coerceAtMost(min(iw, ih) / 2f)
    return ImagePlacement(left, top, left + iw, top + ih, radius)
}

/** True when the output can have see-through pixels and so must be saved as PNG. */
fun BeautifyOptions.needsAlpha(): Boolean = background == Background.Transparent

/**
 * Draws [src] on its background with padding, rounded corners and an optional soft shadow.
 * Software canvas only (BlurMaskFilter). May throw OutOfMemoryError for big outputs.
 */
fun render(src: Bitmap, o: BeautifyOptions, accent: Int, maxEdge: Int = 4096): Bitmap {
    val (w, h) = outputSize(src.width, src.height, o, maxEdge)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(out)
        when (val bg = o.background) {
            is Background.Gradient -> {
                val (_, from, to) = GRADIENT_PRESETS[bg.preset.coerceIn(0, GRADIENT_PRESETS.lastIndex)]
                val paint = Paint().apply {
                    shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), from, to, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            is Background.Solid -> canvas.drawColor(bg.argb)
            Background.Accent -> canvas.drawColor(accent)
            Background.Transparent -> Unit
        }

        val p = placement(src.width, src.height, o, w, h)
        val rect = RectF(p.left, p.top, p.right, p.bottom)
        if (o.shadow) {
            val blur = (max(rect.width(), rect.height()) * 0.03f).coerceAtLeast(1f)
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x4D000000 // black at 30%
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.save()
            canvas.translate(0f, blur * 0.4f)
            canvas.drawRoundRect(rect, p.radius, p.radius, shadow)
            canvas.restore()
        }

        // A shader-filled round rect keeps the corners anti-aliased (clipPath would not).
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setRectToRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), rect, Matrix.ScaleToFit.FILL)
            })
        }
        val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        canvas.drawRoundRect(rect, p.radius, p.radius, image)
    } catch (t: Throwable) {
        out.recycle()
        throw t
    }
    return out
}

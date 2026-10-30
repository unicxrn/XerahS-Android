package com.xerahs.android.feature.tools.combine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

enum class Align { START, CENTER, END }

data class CombineOptions(
    val vertical: Boolean = true,
    val spacingPx: Int = 0,
    val background: Int = 0xFF000000.toInt(),
    val align: Align = Align.CENTER,
    val matchSize: Boolean = true,
)

data class Rect4(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class CombineResult(val width: Int, val height: Int, val rects: List<Rect4>)

/** Cap on the summed pixel count of the decoded inputs (64 MP). */
const val MAX_INPUT_PIXELS: Long = 64_000_000L

/**
 * Places [sizes] (width to height) in a row or column. With matchSize every image is scaled to the
 * widest width (vertical) or tallest height (horizontal). Spacing goes between images only. If the
 * long edge would pass [maxEdge], or the area would pass [maxPixels], everything scales down to
 * fit. Scaled images are laid out again with whole-pixel sizes, so neighbours still touch and gaps
 * stay equal. Sums are done in Long so huge inputs can't overflow.
 */
fun layout(
    sizes: List<Pair<Int, Int>>,
    o: CombineOptions,
    maxEdge: Int = 8000,
    maxPixels: Long = Long.MAX_VALUE,
): CombineResult {
    if (sizes.isEmpty()) return CombineResult(0, 0, emptyList())
    var spacing = o.spacingPx.coerceAtLeast(0).toLong()

    // Each image's size along the stacking axis (main) and across it (cross), unscaled.
    var cross = (if (o.vertical) sizes.maxOf { it.first } else sizes.maxOf { it.second }).toLong().coerceAtLeast(1)
    var placed: List<Pair<Long, Long>> = sizes.map { (w, h) ->
        val (main, across) = if (o.vertical) h.toLong() to w.toLong() else w.toLong() to h.toLong()
        if (o.matchSize && across > 0) (main.toDouble() * cross / across).roundToLong().coerceAtLeast(1) to cross
        else main.coerceAtLeast(1) to across.coerceAtLeast(1)
    }
    val total = placed.sumOf { it.first } + spacing * (placed.size - 1)

    val longEdge = max(total, cross)
    val area = total.toDouble() * cross.toDouble()
    val s = min(
        min(1.0, maxEdge.toDouble() / longEdge),
        if (area > maxPixels) sqrt(maxPixels.toDouble() / area) else 1.0
    )
    if (s < 1.0) {
        fun sc(v: Long) = floor(v * s).toLong().coerceAtLeast(1)
        cross = sc(cross)
        spacing = floor(spacing * s).toLong()
        placed = placed.map { (main, across) -> sc(main) to min(sc(across), cross) }
    }

    var cursor = 0L
    val rects = placed.map { (main, across) ->
        val offset = when (o.align) {
            Align.START -> 0L
            Align.CENTER -> (cross - across) / 2
            Align.END -> cross - across
        }
        val r = if (o.vertical) Rect4(offset.toInt(), cursor.toInt(), (offset + across).toInt(), (cursor + main).toInt())
        else Rect4(cursor.toInt(), offset.toInt(), (cursor + main).toInt(), (offset + across).toInt())
        cursor += main + spacing
        r
    }
    val length = cursor - spacing
    val width = if (o.vertical) cross else length
    val height = if (o.vertical) length else cross
    return CombineResult(width.toInt(), height.toInt(), rects)
}

/**
 * Power-of-two inSampleSize for decoding a [srcW]x[srcH] image that will be drawn at [dstW]x[dstH].
 * Keeps the decode at least as large as the target where possible, then halves further until the
 * decoded pixel count is within [maxPixels].
 */
fun sampleSizeFor(srcW: Int, srcH: Int, dstW: Int, dstH: Int, maxPixels: Long): Int {
    if (srcW <= 0 || srcH <= 0) return 1
    var s = 1
    while (srcW / (s * 2) >= dstW && srcH / (s * 2) >= dstH && s < 1 shl 12) s *= 2
    while ((srcW.toLong() / s) * (srcH.toLong() / s) > maxPixels && s < 1 shl 12) s *= 2
    return s
}

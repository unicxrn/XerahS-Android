package com.xerahs.android.feature.tools.combine

import kotlin.math.max
import kotlin.math.roundToInt

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
 * long edge would pass [maxEdge], the whole canvas and every rect scale down to fit.
 */
fun layout(sizes: List<Pair<Int, Int>>, o: CombineOptions, maxEdge: Int = 8000): CombineResult {
    if (sizes.isEmpty()) return CombineResult(0, 0, emptyList())
    val spacing = o.spacingPx.coerceAtLeast(0)

    // Each image's size along the stacking axis (main) and across it (cross), unscaled.
    val cross = if (o.vertical) sizes.maxOf { it.first } else sizes.maxOf { it.second }
    val placed = sizes.map { (w, h) ->
        val (main, across) = if (o.vertical) h to w else w to h
        if (o.matchSize && across > 0) Math.round(main.toDouble() * cross / across).toInt() to cross
        else main to across
    }
    val total = placed.sumOf { it.first } + spacing * (placed.size - 1)

    var cursor = 0
    val raw = placed.map { (main, across) ->
        val offset = when (o.align) {
            Align.START -> 0
            Align.CENTER -> (cross - across) / 2
            Align.END -> cross - across
        }
        val r = if (o.vertical) Rect4(offset, cursor, offset + across, cursor + main)
        else Rect4(cursor, offset, cursor + main, offset + across)
        cursor += main + spacing
        r
    }
    val width = if (o.vertical) cross else total
    val height = if (o.vertical) total else cross

    val longEdge = max(width, height)
    if (longEdge <= maxEdge) return CombineResult(width, height, raw)

    val s = maxEdge.toDouble() / longEdge
    fun sc(v: Int) = (v * s).roundToInt()
    return CombineResult(
        width = sc(width).coerceIn(1, maxEdge),
        height = sc(height).coerceIn(1, maxEdge),
        rects = raw.map { Rect4(sc(it.left), sc(it.top), sc(it.right), sc(it.bottom)) }
    )
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

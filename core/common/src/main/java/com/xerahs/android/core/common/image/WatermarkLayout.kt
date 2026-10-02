package com.xerahs.android.core.common.image

object WatermarkLayout {
    // Top-left of the text box.
    fun position(imageW: Int, imageH: Int, textW: Float, textH: Float, corner: Corner, margin: Float): Pair<Float, Float> {
        val x = when (corner) { Corner.TOP_LEFT, Corner.BOTTOM_LEFT -> margin; else -> imageW - textW - margin }
        val y = when (corner) { Corner.TOP_LEFT, Corner.TOP_RIGHT -> margin; else -> imageH - textH - margin }
        return x to y
    }
}

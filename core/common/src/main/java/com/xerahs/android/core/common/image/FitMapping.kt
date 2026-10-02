package com.xerahs.android.core.common.image

object FitMapping {
    // Maps a tap in a view showing the bitmap with ContentScale.Fit to bitmap pixel coordinates.
    fun toBitmap(tapX: Float, tapY: Float, viewW: Int, viewH: Int, bmpW: Int, bmpH: Int): Pair<Int, Int>? {
        val scale = minOf(viewW.toFloat() / bmpW, viewH.toFloat() / bmpH)
        val offX = (viewW - bmpW * scale) / 2f
        val offY = (viewH - bmpH * scale) / 2f
        val x = ((tapX - offX) / scale).toInt()
        val y = ((tapY - offY) / scale).toInt()
        return if (x in 0 until bmpW && y in 0 until bmpH) x to y else null
    }
}

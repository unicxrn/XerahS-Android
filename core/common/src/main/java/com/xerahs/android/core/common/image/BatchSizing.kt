package com.xerahs.android.core.common.image

object BatchSizing {
    // Largest size that fits within maxDimension on both sides; 0 = keep original. Never upscales.
    fun target(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        if (maxDimension <= 0 || (width <= maxDimension && height <= maxDimension)) return width to height
        val scale = maxDimension.toFloat() / maxOf(width, height)
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }
}

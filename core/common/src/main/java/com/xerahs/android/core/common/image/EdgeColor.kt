package com.xerahs.android.core.common.image

object EdgeColor {
    // Average opaque colour of the pixels on the border of a width x height block.
    fun average(pixels: IntArray, width: Int, height: Int): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in 0 until height) for (x in 0 until width) {
            if (x != 0 && y != 0 && x != width - 1 && y != height - 1) continue
            val c = pixels[y * width + x]
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
        }
        if (n == 0L) return 0xFFFFFFFF.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
}

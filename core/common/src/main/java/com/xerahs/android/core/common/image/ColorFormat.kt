package com.xerahs.android.core.common.image

object ColorFormat {
    fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)
    fun rgb(argb: Int) = "rgb(${(argb shr 16) and 0xFF}, ${(argb shr 8) and 0xFF}, ${argb and 0xFF})"
    fun parseHex(hex: String): Int? = hex.trim().removePrefix("#").takeIf { it.length == 6 }
        ?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
}

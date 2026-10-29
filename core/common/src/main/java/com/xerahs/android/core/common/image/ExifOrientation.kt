package com.xerahs.android.core.common.image

/** A rotation (clockwise degrees, applied first) and then an optional horizontal flip. */
data class OrientationOp(val degrees: Int, val flipHorizontal: Boolean) {
    val isIdentity: Boolean get() = degrees == 0 && !flipHorizontal
}

/** Maps an EXIF orientation value (1..8) to the operation that makes the pixels upright. Unknown values are identity. */
fun orientationOp(exifOrientation: Int): OrientationOp = when (exifOrientation) {
    2 -> OrientationOp(0, true)
    3 -> OrientationOp(180, false)
    4 -> OrientationOp(180, true)
    5 -> OrientationOp(90, true)
    6 -> OrientationOp(90, false)
    7 -> OrientationOp(270, true)
    8 -> OrientationOp(270, false)
    else -> OrientationOp(0, false)
}

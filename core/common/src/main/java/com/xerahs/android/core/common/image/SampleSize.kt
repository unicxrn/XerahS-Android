package com.xerahs.android.core.common.image

/**
 * Smallest power-of-two BitmapFactory sample size that brings the long edge down to [maxEdge] or
 * less. Returns 1 for unknown sizes or a non-positive cap.
 */
fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    val longEdge = maxOf(width, height)
    if (longEdge <= 0 || maxEdge <= 0) return 1
    var sample = 1
    while (longEdge / sample > maxEdge) sample *= 2
    return sample
}

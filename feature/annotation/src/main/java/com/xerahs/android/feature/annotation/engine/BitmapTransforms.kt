package com.xerahs.android.feature.annotation.engine

import android.graphics.Bitmap
import android.graphics.Matrix

/** A lossless whole-image transform: a quarter-turn rotation or a mirror. */
enum class Op(val degrees: Int) {
    ROTATE_LEFT(-90),
    ROTATE_RIGHT(90),
    ROTATE_180(180),
    FLIP_HORIZONTAL(0),
    FLIP_VERTICAL(0),
}

/**
 * Where pixel ([x], [y]) of a [w] x [h] image lands after [op].
 * Rotations swap the output dimensions, so the result is in an h x w image for quarter turns.
 */
fun mapPoint(x: Int, y: Int, w: Int, h: Int, op: Op): Pair<Int, Int> = when (op) {
    Op.ROTATE_RIGHT -> (h - 1 - y) to x
    Op.ROTATE_LEFT -> y to (w - 1 - x)
    Op.ROTATE_180 -> (w - 1 - x) to (h - 1 - y)
    Op.FLIP_HORIZONTAL -> (w - 1 - x) to y
    Op.FLIP_VERTICAL -> x to (h - 1 - y)
}

/** Rotates by 90, -90 or 180 degrees clockwise. Returns a new bitmap; the receiver is left untouched. */
fun Bitmap.rotated(degrees: Int): Bitmap {
    require(degrees == 90 || degrees == -90 || degrees == 180 || degrees == -180) { "Unsupported rotation $degrees" }
    return transformed(Matrix().apply { setRotate(degrees.toFloat()) })
}

/** Mirrors left to right when [horizontal], otherwise top to bottom. Returns a new bitmap. */
fun Bitmap.flipped(horizontal: Boolean): Bitmap =
    transformed(Matrix().apply { if (horizontal) setScale(-1f, 1f) else setScale(1f, -1f) })

fun Bitmap.transformed(op: Op): Bitmap = when (op) {
    Op.ROTATE_LEFT, Op.ROTATE_RIGHT, Op.ROTATE_180 -> rotated(op.degrees)
    Op.FLIP_HORIZONTAL -> flipped(horizontal = true)
    Op.FLIP_VERTICAL -> flipped(horizontal = false)
}

// createBitmap translates the result back to the origin, so negative scales and rotations need no offset.
private fun Bitmap.transformed(matrix: Matrix): Bitmap =
    Bitmap.createBitmap(this, 0, 0, width, height, matrix, false)

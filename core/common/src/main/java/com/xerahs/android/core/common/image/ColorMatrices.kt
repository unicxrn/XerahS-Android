package com.xerahs.android.core.common.image

object ColorMatrices {
    private const val LR = 0.213f
    private const val LG = 0.715f
    private const val LB = 0.072f

    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    // Returns a matrix that applies [first] and then [then].
    fun concat(then: FloatArray, first: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) sum += then[row * 5 + k] * first[k * 5 + col]
                if (col == 4) sum += then[row * 5 + 4]
                out[row * 5 + col] = sum
            }
        }
        return out
    }

    fun saturation(s: Float) = floatArrayOf(
        LR * (1 - s) + s, LG * (1 - s), LB * (1 - s), 0f, 0f,
        LR * (1 - s), LG * (1 - s) + s, LB * (1 - s), 0f, 0f,
        LR * (1 - s), LG * (1 - s), LB * (1 - s) + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun contrast(c: Float): FloatArray {
        val t = (1 - c) * 128f
        return floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)
    }

    fun brightness(b: Float): FloatArray {
        val o = b * 255f
        return floatArrayOf(1f, 0f, 0f, 0f, o, 0f, 1f, 0f, 0f, o, 0f, 0f, 1f, 0f, o, 0f, 0f, 0f, 1f, 0f)
    }

    val SEPIA = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    val INVERT = floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)

    // Order: saturation, contrast, brightness, grayscale, sepia, invert.
    fun forSettings(s: EffectSettings): FloatArray {
        var m = identity()
        if (s.saturation != 1f) m = concat(saturation(s.saturation), m)
        if (s.contrast != 1f) m = concat(contrast(s.contrast), m)
        if (s.brightness != 0f) m = concat(brightness(s.brightness), m)
        if (s.grayscale) m = concat(saturation(0f), m)
        if (s.sepia) m = concat(SEPIA, m)
        if (s.invert) m = concat(INVERT, m)
        return m
    }

    fun applyToPixel(m: FloatArray, argb: Int): Int {
        val a = (argb ushr 24) and 0xFF; val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        fun ch(row: Int) = (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * a + m[row * 5 + 4])
            .toInt().coerceIn(0, 255)
        return (ch(3) shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
    }
}

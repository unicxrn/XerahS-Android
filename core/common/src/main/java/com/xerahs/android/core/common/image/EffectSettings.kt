package com.xerahs.android.core.common.image

enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

data class Watermark(
    val text: String,
    val corner: Corner = Corner.BOTTOM_RIGHT,
    val opacity: Float = 0.6f,
    // Text height as a fraction of the image's shorter side.
    val sizeFraction: Float = 0.04f,
)

data class EffectSettings(
    val brightness: Float = 0f,      // -1..1
    val contrast: Float = 1f,        // 0..2
    val saturation: Float = 1f,      // 0..2
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
    val invert: Boolean = false,
    val borderWidth: Int = 0,
    val borderColor: Int = 0xFF000000.toInt(),
    val shadow: Boolean = false,
    val cornerRadius: Int = 0,
    val watermark: Watermark? = null,
) {
    val hasColorChange: Boolean
        get() = brightness != 0f || contrast != 1f || saturation != 1f || grayscale || sepia || invert

    val isIdentity: Boolean
        get() = !hasColorChange && borderWidth == 0 && !shadow && cornerRadius == 0 && watermark?.text.isNullOrBlank()
}

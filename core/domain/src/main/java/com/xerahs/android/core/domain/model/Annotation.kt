package com.xerahs.android.core.domain.model

import com.xerahs.android.core.common.generateId

sealed class Annotation {
    abstract val id: String
    abstract val zIndex: Int
    abstract val strokeColor: Int
    abstract val strokeWidth: Float

    data class Rectangle(
        override val id: String = generateId(),
        override val zIndex: Int = 0,
        override val strokeColor: Int = 0xFFFF0000.toInt(),
        override val strokeWidth: Float = 3f,
        val fillColor: Int? = null,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float
    ) : Annotation()

    data class Arrow(
        override val id: String = generateId(),
        override val zIndex: Int = 0,
        override val strokeColor: Int = 0xFFFF0000.toInt(),
        override val strokeWidth: Float = 3f,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val arrowHeadSize: Float = 20f
    ) : Annotation()

    data class Text(
        override val id: String = generateId(),
        override val zIndex: Int = 0,
        override val strokeColor: Int = 0xFFFF0000.toInt(),
        override val strokeWidth: Float = 1f,
        val text: String,
        val x: Float,
        val y: Float,
        val fontSize: Float = 24f,
        val isBold: Boolean = false,
        val isItalic: Boolean = false
    ) : Annotation()

    data class Blur(
        override val id: String = generateId(),
        override val zIndex: Int = 0,
        override val strokeColor: Int = 0x00000000,
        override val strokeWidth: Float = 0f,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val blurRadius: Float = 25f
    ) : Annotation()
}

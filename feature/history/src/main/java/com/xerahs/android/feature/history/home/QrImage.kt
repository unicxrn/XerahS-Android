package com.xerahs.android.feature.history.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.common.qr.QrGenerator

/** Renders [content] as a QR on a WHITE tile (white bg is required for scannability, even in dark theme). */
@Composable
fun QrImage(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) { QrGenerator.generate(content) }
    Canvas(
        modifier = modifier
            .size(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp)
    ) {
        val n = matrix.size
        val cell = size.minDimension / n
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix.isDark(x, y)) {
                drawRect(color = Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell, cell))
            }
        }
    }
}

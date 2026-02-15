package com.xerahs.android.feature.annotation.toolbar

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val hsv = remember {
        val arr = FloatArray(3)
        AndroidColor.colorToHSV(initialColor, arr)
        arr
    }

    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    var hexInput by remember {
        mutableStateOf(String.format("%06X", initialColor and 0xFFFFFF))
    }

    fun currentColor(): Int = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick Color") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // HSV Rectangle picker (hue x saturation)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                                saturation = (1f - offset.y / size.height).coerceIn(0f, 1f)
                                hexInput = String.format("%06X", currentColor() and 0xFFFFFF)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                                saturation = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                hexInput = String.format("%06X", currentColor() and 0xFFFFFF)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    for (x in 0..w.toInt() step 4) {
                        for (y in 0..h.toInt() step 4) {
                            val h2 = x / w * 360f
                            val s2 = 1f - y / h
                            val c = AndroidColor.HSVToColor(floatArrayOf(h2, s2, brightness))
                            drawRect(
                                Color(c),
                                topLeft = Offset(x.toFloat(), y.toFloat()),
                                size = androidx.compose.ui.geometry.Size(4f, 4f)
                            )
                        }
                    }
                    // Indicator
                    val cx = hue / 360f * w
                    val cy = (1f - saturation) * h
                    drawCircle(Color.White, 8f, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Brightness slider
                Text("Brightness", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        hexInput = String.format("%06X", currentColor() and 0xFFFFFF)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Hex input + preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Preview swatch
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(currentColor()))
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )

                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { value ->
                            hexInput = value.take(6).filter { it.isLetterOrDigit() }.uppercase()
                            if (hexInput.length == 6) {
                                try {
                                    val parsed = AndroidColor.parseColor("#$hexInput")
                                    val arr2 = FloatArray(3)
                                    AndroidColor.colorToHSV(parsed, arr2)
                                    hue = arr2[0]
                                    saturation = arr2[1]
                                    brightness = arr2[2]
                                } catch (_: Exception) {}
                            }
                        },
                        label = { Text("Hex") },
                        prefix = { Text("#") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(currentColor()) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

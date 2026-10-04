package com.xerahs.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.domain.model.CustomTheme
import com.xerahs.android.core.ui.lumen.BezelCard
import com.xerahs.android.core.ui.lumen.Eyebrow
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.PillCta

private val presetColors = listOf(
    0xFFE91E63.toInt(), // Pink
    0xFFF44336.toInt(), // Red
    0xFFFF5722.toInt(), // Deep Orange
    0xFFFF9800.toInt(), // Orange
    0xFFFFC107.toInt(), // Amber
    0xFFFFEB3B.toInt(), // Yellow
    0xFF8BC34A.toInt(), // Light Green
    0xFF4CAF50.toInt(), // Green
    0xFF009688.toInt(), // Teal
    0xFF00BCD4.toInt(), // Cyan
    0xFF2196F3.toInt(), // Blue
    0xFF3F51B5.toInt(), // Indigo
    0xFF673AB7.toInt(), // Deep Purple
    0xFF9C27B0.toInt(), // Purple
    0xFF795548.toInt(), // Brown
    0xFF607D8B.toInt(), // Blue Grey
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeEditorScreen(
    existingTheme: CustomTheme? = null,
    onSave: (CustomTheme) -> Unit,
    onBack: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existingTheme?.name ?: "") }
    var hue by rememberSaveable {
        mutableFloatStateOf(
            if (existingTheme != null) {
                val hsv = floatArrayOf(0f, 0f, 0f)
                android.graphics.Color.colorToHSV(existingTheme.seedColor, hsv)
                hsv[0]
            } else 240f
        )
    }
    var saturation by rememberSaveable {
        mutableFloatStateOf(
            if (existingTheme != null) {
                val hsv = floatArrayOf(0f, 0f, 0f)
                android.graphics.Color.colorToHSV(existingTheme.seedColor, hsv)
                hsv[1]
            } else 0.7f
        )
    }

    val seedColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 0.8f))

    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = Lumen.tokens.tint
    )

    Scaffold(
        topBar = { LumenTopBar("Custom theme", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Theme Name") },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Preset colors")
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    presetColors.forEachIndexed { index, color ->
                        val isSelected = seedColor == color
                        AccentSwatch(
                            color = Color(color),
                            selected = isSelected,
                            contentDescription = "Preset color ${index + 1}",
                            onClick = {
                                val hsv = floatArrayOf(0f, 0f, 0f)
                                android.graphics.Color.colorToHSV(color, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow("Hue · ${hue.toInt()}°")
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = sliderColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow("Saturation · ${(saturation * 100).toInt()}%")
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0.1f..1f,
                    colors = sliderColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            BezelCard {
                Column(modifier = Modifier.padding(4.dp)) {
                    Eyebrow("Preview")

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ColorPreviewChip("Primary", Color(seedColor), Modifier.weight(1f))
                        ColorPreviewChip(
                            "Secondary",
                            Color(android.graphics.Color.HSVToColor(floatArrayOf((hue + 30f) % 360f, saturation * 0.5f, 0.7f))),
                            Modifier.weight(1f)
                        )
                        ColorPreviewChip(
                            "Tertiary",
                            Color(android.graphics.Color.HSVToColor(floatArrayOf((hue + 60f) % 360f, saturation * 0.5f, 0.7f))),
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sample card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = Color(
                                android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation * 0.05f, 0.94f))
                            )
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Sample Card",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(seedColor)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This is how content will look with your custom theme.",
                                style = MaterialTheme.typography.bodyMedium,
                                // The card's container is always a near-white pastel, regardless
                                // of app theme, so the body text needs an explicit dark colour
                                // rather than onSurface (which can be near-white in dark mode).
                                color = Color.Black.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }

            PillCta(
                text = "Save theme",
                icon = Icons.Default.Check,
                onClick = {
                    val theme = CustomTheme(
                        id = existingTheme?.id ?: generateId(),
                        name = name.ifBlank { "Custom Theme" },
                        seedColor = seedColor
                    )
                    onSave(theme)
                }
            )
        }
    }
}

@Composable
private fun ColorPreviewChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.medium,
            color = color
        ) {}
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

package com.xerahs.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.common.generateId
import com.xerahs.android.core.domain.model.CustomTheme

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingTheme != null) "Edit Theme" else "New Theme") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val theme = CustomTheme(
                        id = existingTheme?.id ?: generateId(),
                        name = name.ifBlank { "Custom Theme" },
                        seedColor = seedColor
                    )
                    onSave(theme)
                },
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                text = { Text("Save") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Theme Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Preset Colors", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                presetColors.forEach { color ->
                    val isSelected = seedColor == color
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .then(
                                if (isSelected) Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.outline,
                                    CircleShape
                                ) else Modifier
                            )
                            .padding(2.dp)
                            .clickable {
                                val hsv = floatArrayOf(0f, 0f, 0f)
                                android.graphics.Color.colorToHSV(color, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                            }
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(color)
                        ) {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Hue: ${hue.toInt()}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = hue,
                onValueChange = { hue = it },
                valueRange = 0f..360f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Saturation: ${(saturation * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = saturation,
                onValueChange = { saturation = it },
                valueRange = 0.1f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Preview", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            // Color preview strip
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
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
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

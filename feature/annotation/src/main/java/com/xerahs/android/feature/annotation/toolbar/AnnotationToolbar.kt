package com.xerahs.android.feature.annotation.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xerahs.android.feature.annotation.AnnotationTool

@Composable
fun AnnotationToolbar(
    selectedTool: AnnotationTool,
    strokeColor: Int,
    strokeWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    blurRadius: Float,
    textBackgroundEnabled: Boolean,
    opacity: Float,
    hasSelectedAnnotation: Boolean,
    onToolSelected: (AnnotationTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onBlurRadiusChanged: (Float) -> Unit,
    onTextBackgroundChanged: (Boolean) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onDeleteSelected: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = strokeColor,
            onColorSelected = { color ->
                onColorSelected(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Tool selector row — scrollable to fit 6 tools + undo/redo/clear
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tools = listOf(
                Triple(Icons.Default.Rectangle, "Rect", AnnotationTool.RECTANGLE),
                Triple(Icons.Default.NorthEast, "Arrow", AnnotationTool.ARROW),
                Triple(Icons.Default.Circle, "Circle", AnnotationTool.CIRCLE),
                Triple(Icons.Default.Draw, "Free", AnnotationTool.FREEHAND),
                Triple(Icons.Default.TextFields, "Text", AnnotationTool.TEXT),
                Triple(Icons.Default.BlurOn, "Blur", AnnotationTool.BLUR),
                Triple(Icons.Default.FormatListNumbered, "Steps", AnnotationTool.NUMBERED_STEP),
            )

            items(tools) { (icon, label, tool) ->
                ToolButton(
                    icon = icon,
                    label = label,
                    selected = selectedTool == tool,
                    onClick = { onToolSelected(tool) }
                )
            }

            item {
                Spacer(modifier = Modifier.width(8.dp))
            }

            item {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
            }
            item {
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
            }
            item {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                }
            }

            if (hasSelectedAnnotation) {
                item {
                    IconButton(onClick = onDeleteSelected) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = "Delete Selected",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Color picker row — theme-derived palette
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.inversePrimary,
                MaterialTheme.colorScheme.outline,
                Color.White,
                Color.Black
            )

            colors.forEach { color ->
                ColorCircle(
                    color = color,
                    selected = color.toArgb() == strokeColor,
                    onClick = { onColorSelected(color.toArgb()) }
                )
            }

            // "+" button to open full color picker
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, Color.Gray, CircleShape)
                    .clickable { showColorPicker = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Custom color",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Stroke width slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Size",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(36.dp)
            )
            Slider(
                value = strokeWidth,
                onValueChange = onStrokeWidthChanged,
                valueRange = 1f..20f,
                modifier = Modifier.weight(1f)
            )
        }

        // Blur radius slider — only visible when BLUR tool selected
        AnimatedVisibility(visible = selectedTool == AnnotationTool.BLUR) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Blur",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(36.dp)
                )
                Slider(
                    value = blurRadius,
                    onValueChange = onBlurRadiusChanged,
                    valueRange = 5f..50f,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Text background toggle — only visible when TEXT tool selected
        AnimatedVisibility(visible = selectedTool == AnnotationTool.TEXT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = textBackgroundEnabled,
                    onClick = { onTextBackgroundChanged(!textBackgroundEnabled) },
                    label = { Text("Background") }
                )
            }
        }

        // Opacity slider — visible for all tools except blur
        AnimatedVisibility(visible = selectedTool != AnnotationTool.BLUR) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Alpha",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(36.dp)
                )
                Slider(
                    value = opacity,
                    onValueChange = onOpacityChanged,
                    valueRange = 0.1f..1f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = if (selected) {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        ) {
            Icon(icon, contentDescription = label)
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier.border(1.dp, Color.Gray, CircleShape)
            )
            .clickable(onClick = onClick)
    )
}

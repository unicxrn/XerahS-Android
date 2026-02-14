package com.xerahs.android.feature.annotation.toolbar

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onToolSelected: (AnnotationTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Tool selector row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(
                icon = Icons.Default.Rectangle,
                label = "Rect",
                selected = selectedTool == AnnotationTool.RECTANGLE,
                onClick = { onToolSelected(AnnotationTool.RECTANGLE) }
            )
            ToolButton(
                icon = Icons.Default.NorthEast,
                label = "Arrow",
                selected = selectedTool == AnnotationTool.ARROW,
                onClick = { onToolSelected(AnnotationTool.ARROW) }
            )
            ToolButton(
                icon = Icons.Default.TextFields,
                label = "Text",
                selected = selectedTool == AnnotationTool.TEXT,
                onClick = { onToolSelected(AnnotationTool.TEXT) }
            )
            ToolButton(
                icon = Icons.Default.BlurOn,
                label = "Blur",
                selected = selectedTool == AnnotationTool.BLUR,
                onClick = { onToolSelected(AnnotationTool.BLUR) }
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = "Clear")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Color picker row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val colors = listOf(
                Color.Red, Color.Blue, Color.Green, Color.Yellow,
                Color.Cyan, Color.Magenta, Color.White, Color.Black
            )

            colors.forEach { color ->
                ColorCircle(
                    color = color,
                    selected = color.toArgb() == strokeColor,
                    onClick = { onColorSelected(color.toArgb()) }
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

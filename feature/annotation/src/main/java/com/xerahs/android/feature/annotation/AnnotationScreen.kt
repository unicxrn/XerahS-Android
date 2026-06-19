package com.xerahs.android.feature.annotation

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.domain.model.Annotation
import com.xerahs.android.feature.annotation.canvas.AnnotationCanvas
import com.xerahs.android.feature.annotation.crop.CropEngine
import com.xerahs.android.feature.annotation.crop.CropOverlay
import com.xerahs.android.feature.annotation.engine.AnnotationEngine
import com.xerahs.android.feature.annotation.toolbar.ColorPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationScreen(
    imagePath: String,
    onExportComplete: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AnnotationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentBitmap by remember(imagePath) {
        mutableStateOf(BitmapFactory.decodeFile(imagePath))
    }

    if (currentBitmap == null) {
        Text("Failed to load image", color = MaterialTheme.colorScheme.error)
        return
    }

    val bitmap = currentBitmap!!

    // In-progress drag state
    var dragStartPos by remember { mutableStateOf<Offset?>(null) }
    var currentDragAnnotation by remember { mutableStateOf<Annotation?>(null) }
    var freehandPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }

    // Crop state
    var cropRect by remember { mutableStateOf(android.graphics.Rect(0, 0, bitmap.width, bitmap.height)) }

    // Contextual options sheet
    var showToolOptions by remember { mutableStateOf(false) }

    // Text input dialog (new or edit)
    if (uiState.pendingTextPosition != null) {
        val isEditing = uiState.editingAnnotationId != null
        val existingText = if (isEditing) {
            (uiState.annotations.find { it.id == uiState.editingAnnotationId } as? Annotation.Text)?.text ?: ""
        } else ""
        var textInput by remember(uiState.editingAnnotationId, uiState.pendingTextPosition) {
            mutableStateOf(existingText)
        }
        AlertDialog(
            onDismissRequest = {
                if (isEditing) viewModel.cancelEditText() else viewModel.dismissTextDialog()
            },
            title = { Text(if (isEditing) "Edit Text" else "Enter Text") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Annotation text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            if (isEditing) {
                                viewModel.updateTextAnnotation(textInput)
                            } else {
                                viewModel.addTextAnnotation(textInput)
                            }
                        } else {
                            if (isEditing) viewModel.cancelEditText() else viewModel.dismissTextDialog()
                        }
                    }
                ) { Text(if (isEditing) "Update" else "Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (isEditing) viewModel.cancelEditText() else viewModel.dismissTextDialog()
                }) { Text("Cancel") }
            }
        )
    }

    // Contextual tool-options bottom sheet
    if (showToolOptions && !uiState.isCropMode) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showToolOptions = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            ToolOptionsSheet(
                uiState = uiState,
                onColorSelected = viewModel::setStrokeColor,
                onStrokeWidthChanged = viewModel::setStrokeWidth,
                onBlurRadiusChanged = viewModel::setBlurRadius,
                onMagnifyZoomChanged = viewModel::setMagnifyZoom,
                onOpacityChanged = viewModel::setOpacity,
                onFillEnabledChanged = viewModel::setFillEnabled,
                onFontSizeChanged = viewModel::setFontSize,
                onTextBackgroundChanged = viewModel::setTextBackgroundEnabled
            )
        }
    }

    // OCR result bottom sheet
    if (uiState.ocrText != null || uiState.ocrError != null) {
        val ocrSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val clipboardManager = LocalClipboardManager.current
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissOcr() },
            sheetState = ocrSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Text from image",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                val ocrError = uiState.ocrError
                val ocrText = uiState.ocrText
                when {
                    ocrError != null -> {
                        Text(
                            text = ocrError,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    ocrText.isNullOrBlank() -> {
                        Text(
                            text = "No text found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        SelectionContainer {
                            Text(
                                text = ocrText,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(ocrText))
                                    viewModel.dismissOcr()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Copy")
                            }
                            TextButton(
                                onClick = {
                                    val shareIntent = Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, ocrText)
                                        },
                                        null
                                    )
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Share")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Canvas-first layout: image fills the whole surface, overlays float on top.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Full-bleed canvas / crop area
        if (uiState.isCropMode) {
            CropOverlay(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                onCropRectChanged = { cropRect = it },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AnnotationCanvas(
                bitmap = bitmap,
                annotations = uiState.annotations,
                currentAnnotation = currentDragAnnotation,
                selectedAnnotationId = uiState.selectedAnnotationId,
                onAnnotationTapped = { id -> viewModel.selectAnnotation(id) },
                onTextAnnotationTapped = { id -> viewModel.startEditTextAnnotation(id) },
                onDragStart = { offset ->
                    if (uiState.selectedTool == AnnotationTool.NUMBERED_STEP) {
                        viewModel.addNumberedStep(offset.x, offset.y)
                        return@AnnotationCanvas
                    }
                    dragStartPos = offset
                    if (uiState.selectedTool == AnnotationTool.FREEHAND) {
                        freehandPoints = listOf(Pair(offset.x, offset.y))
                    }
                },
                onDrag = { offset ->
                    if (uiState.selectedTool == AnnotationTool.NUMBERED_STEP) return@AnnotationCanvas
                    if (uiState.selectedTool == AnnotationTool.FREEHAND) {
                        freehandPoints = freehandPoints + Pair(offset.x, offset.y)
                        currentDragAnnotation = Annotation.Freehand(
                            id = "in_progress",
                            strokeColor = uiState.strokeColor,
                            strokeWidth = uiState.strokeWidth,
                            opacity = uiState.opacity,
                            points = freehandPoints
                        )
                    } else {
                        dragStartPos?.let { start ->
                            currentDragAnnotation = createInProgressAnnotation(
                                tool = uiState.selectedTool,
                                start = start,
                                current = offset,
                                strokeColor = uiState.strokeColor,
                                strokeWidth = uiState.strokeWidth,
                                blurRadius = uiState.blurRadius,
                                magnifyZoom = uiState.magnifyZoom,
                                opacity = uiState.opacity,
                                fillColor = if (uiState.fillEnabled) uiState.fillColor else null
                            )
                        }
                    }
                },
                onDragEnd = { offset ->
                    if (uiState.selectedTool == AnnotationTool.NUMBERED_STEP) return@AnnotationCanvas
                    if (uiState.selectedTool == AnnotationTool.FREEHAND) {
                        viewModel.addFreehandAnnotation(freehandPoints)
                        freehandPoints = emptyList()
                    } else {
                        dragStartPos?.let { start ->
                            viewModel.addAnnotation(start.x, start.y, offset.x, offset.y)
                        }
                    }
                    dragStartPos = null
                    currentDragAnnotation = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Slim translucent top bar overlay
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (uiState.isCropMode) viewModel.setCropMode(false) else onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.weight(1f))

                if (uiState.isCropMode) {
                    TextButton(onClick = {
                        val cropped = CropEngine.cropBitmap(bitmap, cropRect)
                        currentBitmap = cropped
                        viewModel.setCropMode(false)
                        viewModel.clearAnnotations()
                    }) {
                        Text("Apply Crop")
                    }
                } else {
                    IconButton(onClick = viewModel::undo, enabled = uiState.undoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = viewModel::redo, enabled = uiState.redoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { viewModel.setCropMode(true) }) {
                        Icon(Icons.Default.Crop, contentDescription = "Crop")
                    }
                    if (uiState.isRecognizing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.recognizeText(imagePath) }) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = "Extract text")
                        }
                    }
                }
            }
        }

        // Bottom controls: floating tool bar + primary action
        if (!uiState.isCropMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Compact floating tool bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolButtons.forEach { (icon, label, tool) ->
                            CompactToolButton(
                                icon = icon,
                                label = label,
                                selected = uiState.selectedTool == tool,
                                onClick = {
                                    if (uiState.selectedTool == tool) {
                                        showToolOptions = true
                                    } else {
                                        viewModel.selectTool(tool)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Options affordance for the currently selected tool
                        CompactIconButton(
                            icon = Icons.Default.Tune,
                            contentDescription = "Tool options",
                            onClick = { showToolOptions = true }
                        )

                        if (uiState.selectedAnnotationId != null) {
                            CompactIconButton(
                                icon = Icons.Default.DeleteForever,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error,
                                onClick = viewModel::deleteSelectedAnnotation
                            )
                        }

                        CompactIconButton(
                            icon = Icons.Default.Delete,
                            contentDescription = "Clear all",
                            onClick = viewModel::clearAnnotations
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Thumb-zone primary action - same export path as the old checkmark
                Button(
                    onClick = {
                        viewModel.setExporting(true)
                        coroutineScope.launch {
                            val exported = withContext(Dispatchers.Default) {
                                val annotatedBitmap = AnnotationEngine.renderAnnotations(
                                    bitmap, uiState.annotations
                                )
                                val exportsDir = File(context.filesDir, "exports")
                                if (!exportsDir.exists()) exportsDir.mkdirs()
                                val exportFile = File(exportsDir, "export_${System.currentTimeMillis()}.png")
                                AnnotationEngine.exportToFile(annotatedBitmap, exportFile)
                                annotatedBitmap.recycle()
                                exportFile.absolutePath
                            }
                            viewModel.setExporting(false)
                            onExportComplete(exported)
                        }
                    },
                    enabled = !uiState.isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Continue")
                    }
                }
            }
        }
    }
}

private val ToolButtons: List<Triple<ImageVector, String, AnnotationTool>> = listOf(
    Triple(Icons.Default.Rectangle, "Rect", AnnotationTool.RECTANGLE),
    Triple(Icons.Default.NorthEast, "Arrow", AnnotationTool.ARROW),
    Triple(Icons.Default.Circle, "Circle", AnnotationTool.CIRCLE),
    Triple(Icons.Default.Draw, "Free", AnnotationTool.FREEHAND),
    Triple(Icons.Default.TextFields, "Text", AnnotationTool.TEXT),
    Triple(Icons.Default.BlurOn, "Blur", AnnotationTool.BLUR),
    Triple(Icons.Default.FormatListNumbered, "Steps", AnnotationTool.NUMBERED_STEP),
    Triple(Icons.Default.HorizontalRule, "Line", AnnotationTool.LINE),
    Triple(Icons.Default.Highlight, "Mark", AnnotationTool.HIGHLIGHT),
    Triple(Icons.Default.GridOn, "Pixel", AnnotationTool.PIXELATE),
    Triple(Icons.Default.HighlightAlt, "Spot", AnnotationTool.SPOTLIGHT),
    Triple(Icons.Default.ZoomIn, "Zoom", AnnotationTool.MAGNIFY)
)

@Composable
private fun CompactToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = content)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
private fun ToolOptionsSheet(
    uiState: AnnotationUiState,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onBlurRadiusChanged: (Float) -> Unit,
    onMagnifyZoomChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onFillEnabledChanged: (Boolean) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onTextBackgroundChanged: (Boolean) -> Unit
) {
    val tool = uiState.selectedTool
    val title = when (tool) {
        AnnotationTool.RECTANGLE -> "Rectangle"
        AnnotationTool.ARROW -> "Arrow"
        AnnotationTool.CIRCLE -> "Circle"
        AnnotationTool.FREEHAND -> "Freehand"
        AnnotationTool.TEXT -> "Text"
        AnnotationTool.BLUR -> "Blur"
        AnnotationTool.NUMBERED_STEP -> "Numbered Step"
        AnnotationTool.LINE -> "Line"
        AnnotationTool.HIGHLIGHT -> "Highlight"
        AnnotationTool.PIXELATE -> "Pixelate"
        AnnotationTool.SPOTLIGHT -> "Spotlight"
        AnnotationTool.MAGNIFY -> "Magnify"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        val showColor = tool != AnnotationTool.BLUR
        if (showColor) {
            ColorSwatchRow(strokeColor = uiState.strokeColor, onColorSelected = onColorSelected)
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (tool) {
            AnnotationTool.RECTANGLE, AnnotationTool.CIRCLE -> {
                LabeledSlider("Stroke", uiState.strokeWidth, 1f..20f, onStrokeWidthChanged)
                FilterChip(
                    selected = uiState.fillEnabled,
                    onClick = { onFillEnabledChanged(!uiState.fillEnabled) },
                    label = { Text("Fill") },
                    modifier = Modifier.padding(top = 4.dp)
                )
                LabeledSlider("Alpha", uiState.opacity, 0.1f..1f, onOpacityChanged)
            }
            AnnotationTool.ARROW, AnnotationTool.FREEHAND, AnnotationTool.LINE -> {
                LabeledSlider("Stroke", uiState.strokeWidth, 1f..20f, onStrokeWidthChanged)
                LabeledSlider("Alpha", uiState.opacity, 0.1f..1f, onOpacityChanged)
            }
            AnnotationTool.BLUR, AnnotationTool.PIXELATE -> {
                LabeledSlider("Blur", uiState.blurRadius, 5f..50f, onBlurRadiusChanged)
            }
            AnnotationTool.HIGHLIGHT -> {
                LabeledSlider("Alpha", uiState.opacity, 0.1f..1f, onOpacityChanged)
            }
            AnnotationTool.TEXT -> {
                LabeledSlider("Font", uiState.fontSize, 12f..72f, onFontSizeChanged)
                FilterChip(
                    selected = uiState.textBackgroundEnabled,
                    onClick = { onTextBackgroundChanged(!uiState.textBackgroundEnabled) },
                    label = { Text("Background") },
                    modifier = Modifier.padding(top = 4.dp)
                )
                LabeledSlider("Alpha", uiState.opacity, 0.1f..1f, onOpacityChanged)
            }
            AnnotationTool.NUMBERED_STEP -> {
                LabeledSlider("Alpha", uiState.opacity, 0.1f..1f, onOpacityChanged)
            }
            AnnotationTool.SPOTLIGHT -> {
                LabeledSlider("Dim", uiState.opacity, 0.1f..1f, onOpacityChanged)
            }
            AnnotationTool.MAGNIFY -> {
                LabeledSlider("Zoom", uiState.magnifyZoom, 1.5f..4f, onMagnifyZoomChanged)
                LabeledSlider("Stroke", uiState.strokeWidth, 1f..20f, onStrokeWidthChanged)
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    strokeColor: Int,
    onColorSelected: (Int) -> Unit
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (color.toArgb() == strokeColor) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        }
                    )
                    .clickable { onColorSelected(color.toArgb()) }
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { showColorPicker = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Custom color",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(52.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun createInProgressAnnotation(
    tool: AnnotationTool,
    start: Offset,
    current: Offset,
    strokeColor: Int,
    strokeWidth: Float,
    blurRadius: Float,
    magnifyZoom: Float = 2f,
    opacity: Float,
    fillColor: Int? = null
): Annotation? {
    return when (tool) {
        AnnotationTool.RECTANGLE -> Annotation.Rectangle(
            id = "in_progress",
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            opacity = opacity,
            fillColor = fillColor,
            startX = start.x, startY = start.y,
            endX = current.x, endY = current.y
        )
        AnnotationTool.ARROW -> Annotation.Arrow(
            id = "in_progress",
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            opacity = opacity,
            startX = start.x, startY = start.y,
            endX = current.x, endY = current.y
        )
        AnnotationTool.TEXT -> null
        AnnotationTool.BLUR -> Annotation.Blur(
            id = "in_progress",
            opacity = opacity,
            startX = start.x, startY = start.y,
            endX = current.x, endY = current.y,
            blurRadius = blurRadius
        )
        AnnotationTool.CIRCLE -> {
            val centerX = (start.x + current.x) / 2f
            val centerY = (start.y + current.y) / 2f
            val dx = current.x - start.x
            val dy = current.y - start.y
            val radius = kotlin.math.sqrt(dx * dx + dy * dy) / 2f
            Annotation.Circle(
                id = "in_progress",
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                opacity = opacity,
                fillColor = fillColor,
                centerX = centerX,
                centerY = centerY,
                radius = radius
            )
        }
        AnnotationTool.FREEHAND -> null
        AnnotationTool.NUMBERED_STEP -> null
        AnnotationTool.LINE -> Annotation.Line(
            id = "in_progress",
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            opacity = opacity,
            startX = start.x, startY = start.y,
            endX = current.x, endY = current.y
        )
        AnnotationTool.HIGHLIGHT -> Annotation.Highlight(
            id = "in_progress",
            strokeColor = strokeColor,
            opacity = opacity,
            startX = start.x, startY = start.y,
            endX = current.x, endY = current.y
        )
        AnnotationTool.PIXELATE -> Annotation.Pixelate(
            id = "in_progress",
            opacity = opacity,
            startX = start.x, startY = start.y,
            endX = current.x, endY = current.y
        )
        AnnotationTool.SPOTLIGHT -> Annotation.Spotlight(
            id = "in_progress",
            opacity = opacity,
            startX = start.x, startY = start.y,
            endX = current.x, endY = current.y
        )
        AnnotationTool.MAGNIFY -> {
            val dx = current.x - start.x
            val dy = current.y - start.y
            val radius = kotlin.math.sqrt(dx * dx + dy * dy)
            Annotation.Magnify(
                id = "in_progress",
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                opacity = opacity,
                centerX = start.x,
                centerY = start.y,
                radius = radius,
                zoom = magnifyZoom
            )
        }
    }
}

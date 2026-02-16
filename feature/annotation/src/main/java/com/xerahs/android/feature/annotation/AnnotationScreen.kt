package com.xerahs.android.feature.annotation

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.domain.model.Annotation
import com.xerahs.android.feature.annotation.canvas.AnnotationCanvas
import com.xerahs.android.feature.annotation.crop.CropEngine
import com.xerahs.android.feature.annotation.crop.CropOverlay
import com.xerahs.android.feature.annotation.engine.AnnotationEngine
import com.xerahs.android.feature.annotation.toolbar.AnnotationToolbar
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isCropMode) "Crop" else "Annotate") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isCropMode) viewModel.setCropMode(false) else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                        IconButton(onClick = { viewModel.setCropMode(true) }) {
                            Icon(Icons.Default.Crop, contentDescription = "Crop")
                        }
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                        } else {
                            IconButton(onClick = {
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
                            }) {
                                Icon(Icons.Default.Done, contentDescription = "Export")
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isCropMode) {
                CropOverlay(
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    onCropRectChanged = { cropRect = it },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Canvas area
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
                    modifier = Modifier.weight(1f)
                )

                // Toolbar
                AnnotationToolbar(
                    selectedTool = uiState.selectedTool,
                    strokeColor = uiState.strokeColor,
                    strokeWidth = uiState.strokeWidth,
                    canUndo = uiState.undoStack.isNotEmpty(),
                    canRedo = uiState.redoStack.isNotEmpty(),
                    blurRadius = uiState.blurRadius,
                    textBackgroundEnabled = uiState.textBackgroundEnabled,
                    opacity = uiState.opacity,
                    hasSelectedAnnotation = uiState.selectedAnnotationId != null,
                    fillEnabled = uiState.fillEnabled,
                    fillColor = uiState.fillColor,
                    fontSize = uiState.fontSize,
                    onToolSelected = viewModel::selectTool,
                    onColorSelected = viewModel::setStrokeColor,
                    onStrokeWidthChanged = viewModel::setStrokeWidth,
                    onBlurRadiusChanged = viewModel::setBlurRadius,
                    onTextBackgroundChanged = viewModel::setTextBackgroundEnabled,
                    onOpacityChanged = viewModel::setOpacity,
                    onFillEnabledChanged = viewModel::setFillEnabled,
                    onFillColorChanged = viewModel::setFillColor,
                    onFontSizeChanged = viewModel::setFontSize,
                    onDeleteSelected = viewModel::deleteSelectedAnnotation,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onClear = viewModel::clearAnnotations
                )
            }
        }
    }
}

private fun createInProgressAnnotation(
    tool: AnnotationTool,
    start: Offset,
    current: Offset,
    strokeColor: Int,
    strokeWidth: Float,
    blurRadius: Float,
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
    }
}

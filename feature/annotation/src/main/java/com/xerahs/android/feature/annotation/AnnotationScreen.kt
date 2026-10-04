package com.xerahs.android.feature.annotation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.EmojiEmotions
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.common.image.ImageEffects
import com.xerahs.android.core.domain.model.Annotation
import com.xerahs.android.core.ui.lumen.CircleIconButton
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.PillCta
import com.xerahs.android.core.ui.lumen.monoStyle
import com.xerahs.android.feature.annotation.canvas.AnnotationCanvas
import com.xerahs.android.feature.annotation.canvas.SmartEraserSampler
import com.xerahs.android.feature.annotation.crop.CropEngine
import com.xerahs.android.feature.annotation.crop.CropOverlay
import com.xerahs.android.feature.annotation.effects.EffectsSheet
import com.xerahs.android.feature.annotation.engine.AnnotationEngine
import com.xerahs.android.feature.annotation.toolbar.ColorPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) coroutineScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "stickers").apply { mkdirs() }
                    val out = File(dir, "sticker_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                    out.absolutePath
                }.getOrNull()
            }
            path?.let(viewModel::setStickerImage)
        }
    }
    val pickSticker = {
        stickerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Contextual options sheet
    var showToolOptions by remember { mutableStateOf(false) }

    // Effects panel state
    var showEffects by remember { mutableStateOf(false) }
    var showBorderColor by remember { mutableStateOf(false) }
    val effectsThumb = remember(bitmap) {
        val s = 512f / maxOf(bitmap.width, bitmap.height)
        if (s >= 1f) bitmap else Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * s).toInt().coerceAtLeast(1), (bitmap.height * s).toInt().coerceAtLeast(1), true
        )
    }
    var displayBitmap by remember(bitmap) { mutableStateOf(bitmap) }
    LaunchedEffect(bitmap, uiState.effects) {
        delay(150)
        displayBitmap = withContext(Dispatchers.Default) { ImageEffects.applyColor(bitmap, uiState.effects) }
    }

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
            containerColor = MaterialTheme.colorScheme.background
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
                onTextBackgroundChanged = viewModel::setTextBackgroundEnabled,
                onBalloonTextChanged = viewModel::setBalloonText,
                onPickSticker = pickSticker
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
            containerColor = MaterialTheme.colorScheme.background
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

    // Effects bottom sheet
    if (showEffects) {
        val effectsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEffects = false },
            sheetState = effectsSheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            EffectsSheet(
                effects = uiState.effects,
                thumbnail = effectsThumb,
                onChange = viewModel::updateEffects,
                onPickBorderColor = { showBorderColor = true },
                onReset = viewModel::resetEffects
            )
        }
    }
    if (showBorderColor) {
        ColorPickerDialog(
            initialColor = uiState.effects.borderColor,
            onColorSelected = { c ->
                viewModel.updateEffects { it.copy(borderColor = c) }
                showBorderColor = false
            },
            onDismiss = { showBorderColor = false }
        )
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
            // Bezel frame around the canvas. Insets: 64dp + status bar on top (clears the
            // chrome bar), 14dp on the sides, and 144dp on the bottom (62dp toolbar + 10dp
            // spacer + 60dp pill + 12dp column padding) + the nav bar inset, so the frame sits
            // above the floating toolbar instead of behind it. The bezel's clip/padding is on
            // this wrapping Box, not inside AnnotationCanvas's own modifier, so the canvas keeps
            // computing its fit-scale and touch mapping from its own actual (inset) size.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
                    .padding(top = 64.dp, start = 14.dp, end = 14.dp, bottom = 144.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, Lumen.tokens.hairline, RoundedCornerShape(30.dp))
                    .padding(5.dp)
                    .clip(RoundedCornerShape(25.dp))
            ) {
            AnnotationCanvas(
                bitmap = displayBitmap,
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
                    if (uiState.selectedTool == AnnotationTool.FREEHAND ||
                        uiState.selectedTool == AnnotationTool.HIGHLIGHTER_PEN
                    ) {
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
                    } else if (uiState.selectedTool == AnnotationTool.HIGHLIGHTER_PEN) {
                        freehandPoints = freehandPoints + Pair(offset.x, offset.y)
                        currentDragAnnotation = Annotation.HighlighterPen(
                            id = "in_progress",
                            strokeColor = uiState.strokeColor,
                            strokeWidth = maxOf(uiState.strokeWidth, 12f) * 3f,
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
                    if (uiState.selectedTool == AnnotationTool.FREEHAND ||
                        uiState.selectedTool == AnnotationTool.HIGHLIGHTER_PEN
                    ) {
                        viewModel.addFreehandAnnotation(freehandPoints)
                        freehandPoints = emptyList()
                    } else {
                        dragStartPos?.let { start ->
                            viewModel.addAnnotation(
                                start.x, start.y, offset.x, offset.y,
                                sampledColor = if (uiState.selectedTool == AnnotationTool.SMART_ERASER) {
                                    SmartEraserSampler.sample(bitmap, start.x, start.y, offset.x, offset.y)
                                } else {
                                    null
                                }
                            )
                        }
                    }
                    dragStartPos = null
                    currentDragAnnotation = null
                },
                modifier = Modifier.fillMaxSize()
            )
            }
        }

        // Transparent top bar overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = { if (uiState.isCropMode) viewModel.setCropMode(false) else onBack() }
            )
            Text(
                text = File(imagePath).name,
                style = monoStyle(12),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (uiState.isCropMode) {
                Button(
                    onClick = {
                        val cropped = CropEngine.cropBitmap(bitmap, cropRect)
                        currentBitmap = cropped
                        viewModel.setCropMode(false)
                        viewModel.clearAnnotations()
                    },
                    shape = CircleShape
                ) {
                    Text("Apply Crop")
                }
            } else {
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Lumen.tokens.hairline, CircleShape),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = viewModel::undo,
                        enabled = uiState.undoStack.isNotEmpty(),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = viewModel::redo,
                        enabled = uiState.redoStack.isNotEmpty(),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Lumen.tokens.tint)
                        .clickable(onClick = { showEffects = true }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Effects", tint = Lumen.tokens.ink)
                }
                CircleIconButton(
                    icon = Icons.Default.Crop,
                    contentDescription = "Crop",
                    onClick = { viewModel.setCropMode(true) }
                )
                if (uiState.isRecognizing) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    CircleIconButton(
                        icon = Icons.Default.DocumentScanner,
                        contentDescription = "Extract text",
                        onClick = { viewModel.recognizeText(imagePath) }
                    )
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
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Lumen.tokens.hairline),
                    modifier = Modifier.fillMaxWidth().height(62.dp)
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
                                    if (tool == AnnotationTool.STICKER && uiState.pendingStickerPath == null) {
                                        pickSticker()
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
                PillCta(
                    text = "Continue to upload",
                    onClick = {
                        viewModel.setExporting(true)
                        coroutineScope.launch {
                            val exported = withContext(Dispatchers.Default) {
                                val annotatedBitmap = AnnotationEngine.renderAnnotations(
                                    bitmap, uiState.annotations
                                )
                                val finalBitmap = if (uiState.effects.isIdentity) annotatedBitmap
                                    else ImageEffects.apply(annotatedBitmap, uiState.effects)
                                val exportsDir = File(context.filesDir, "exports")
                                if (!exportsDir.exists()) exportsDir.mkdirs()
                                val exportFile = File(exportsDir, "export_${System.currentTimeMillis()}.png")
                                AnnotationEngine.exportToFile(finalBitmap, exportFile)
                                annotatedBitmap.recycle()
                                if (finalBitmap !== annotatedBitmap) finalBitmap.recycle()
                                exportFile.absolutePath
                            }
                            viewModel.setExporting(false)
                            onExportComplete(exported)
                        }
                    },
                    enabled = !uiState.isExporting,
                    loading = uiState.isExporting
                )
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
    Triple(Icons.Default.ZoomIn, "Zoom", AnnotationTool.MAGNIFY),
    Triple(Icons.Default.ChatBubbleOutline, "Balloon", AnnotationTool.SPEECH_BALLOON),
    Triple(Icons.Default.EmojiEmotions, "Sticker", AnnotationTool.STICKER),
    Triple(Icons.Default.AutoFixOff, "Erase", AnnotationTool.SMART_ERASER),
    Triple(Icons.Default.BorderColor, "Marker", AnnotationTool.HIGHLIGHTER_PEN)
)

@Composable
private fun CompactToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = content)
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
    onTextBackgroundChanged: (Boolean) -> Unit,
    onBalloonTextChanged: (String) -> Unit,
    onPickSticker: () -> Unit
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
        AnnotationTool.SPEECH_BALLOON -> "Speech balloon"
        AnnotationTool.STICKER -> "Sticker"
        AnnotationTool.SMART_ERASER -> "Smart eraser"
        AnnotationTool.HIGHLIGHTER_PEN -> "Highlighter pen"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        val showColor = tool != AnnotationTool.BLUR &&
            tool != AnnotationTool.SMART_ERASER &&
            tool != AnnotationTool.STICKER
        if (showColor) {
            ColorSwatchRow(strokeColor = uiState.strokeColor, strokeWidth = uiState.strokeWidth, onColorSelected = onColorSelected)
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
            AnnotationTool.SPEECH_BALLOON -> {
                OutlinedTextField(
                    value = uiState.balloonText,
                    onValueChange = onBalloonTextChanged,
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth()
                )
                LabeledSlider("Size", uiState.fontSize, 12f..96f, onFontSizeChanged)
            }
            AnnotationTool.STICKER -> {
                Button(onClick = onPickSticker) {
                    Text(if (uiState.pendingStickerPath == null) "Choose image" else "Change image")
                }
            }
            AnnotationTool.SMART_ERASER -> {
                Text(
                    "Drag over text or UI on a plain background. The area is filled with the colour around it.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AnnotationTool.HIGHLIGHTER_PEN -> {
                LabeledSlider("Width", uiState.strokeWidth, 1f..20f, onStrokeWidthChanged)
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    strokeColor: Int,
    strokeWidth: Float,
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { color ->
            val selected = color.toArgb() == strokeColor
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onColorSelected(color.toArgb()) }
                )
            }
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { showColorPicker = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Custom color",
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = "${strokeWidth.toInt()}px",
            style = monoStyle(11),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
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
        AnnotationTool.SPEECH_BALLOON -> Annotation.SpeechBalloon(
            id = "in_progress", strokeColor = strokeColor, strokeWidth = strokeWidth,
            startX = start.x, startY = start.y, endX = current.x, endY = current.y,
            tailX = minOf(start.x, current.x) + kotlin.math.abs(current.x - start.x) * 0.25f,
            tailY = maxOf(start.y, current.y) + 24f, text = ""
        )
        AnnotationTool.STICKER -> null
        AnnotationTool.SMART_ERASER -> Annotation.SmartEraser(
            id = "in_progress", startX = start.x, startY = start.y, endX = current.x, endY = current.y,
            fillColor = 0x80808080.toInt()
        )
        AnnotationTool.HIGHLIGHTER_PEN -> null
    }
}

package com.xerahs.android.feature.tools.combine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xerahs.android.core.ui.lumen.CircleIconButton
import com.xerahs.android.core.ui.lumen.Eyebrow
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.LumenCard
import com.xerahs.android.core.ui.lumen.LumenSwitch
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.PillCta
import com.xerahs.android.core.ui.lumen.SegmentOption
import com.xerahs.android.core.ui.lumen.SegmentedTiles
import com.xerahs.android.core.ui.lumen.monoStyle
import com.xerahs.android.feature.tools.ToolOutput
import com.xerahs.android.feature.tools.rememberPicturesWriteAccess
import kotlinx.coroutines.launch

private val backgrounds = listOf(
    0xFF000000.toInt() to "Black",
    0xFFFFFFFF.toInt() to "White",
    0xFF808080.toInt() to "Gray",
    0x00000000 to "Transparent",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombineScreen(
    onBack: () -> Unit,
    onUpload: (String) -> Unit,
    viewModel: CombineViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val paths by viewModel.paths.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val thumbs by viewModel.thumbs.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val rendering by viewModel.rendering.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = CombineViewModel.MAX_IMAGES)
    ) { picked -> viewModel.add(picked) }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val scope = rememberCoroutineScope()
    // Android 9 and older need storage access to write to Pictures.
    val save = rememberPicturesWriteAccess { granted ->
        if (granted) viewModel.run(CombineAction.SAVE)
        else scope.launch { snackbar.showSnackbar("Saving to Pictures needs storage access") }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is CombineEvent.Upload -> onUpload(e.path)
                is CombineEvent.Share -> runCatching { ToolOutput.share(context, e.file) }
                    .onFailure { snackbar.showSnackbar("Couldn't open the share sheet") }
                is CombineEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    Scaffold(
        topBar = { LumenTopBar(title = "Combine images", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (paths.isEmpty()) {
                Text(
                    "Pick 2 to ${CombineViewModel.MAX_IMAGES} images to stack them into one, top to bottom or side by side.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PillCta(text = "Choose images", onClick = pick, loading = importing)
            } else {
                CombineEditor(
                    paths = paths,
                    options = options,
                    thumbs = thumbs,
                    preview = preview?.asImageBitmap(),
                    rendering = rendering,
                    importing = importing,
                    busy = busy,
                    onPick = pick,
                    onSave = save,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun CombineEditor(
    paths: List<String>,
    options: CombineOptions,
    thumbs: Map<String, android.graphics.Bitmap>,
    preview: androidx.compose.ui.graphics.ImageBitmap?,
    rendering: Boolean,
    importing: Boolean,
    busy: CombineAction?,
    onPick: () -> Unit,
    onSave: () -> Unit,
    viewModel: CombineViewModel,
) {
    val pick = onPick
    PreviewCard(preview = preview, rendering = rendering, enough = paths.size >= CombineViewModel.MIN_IMAGES)

    Eyebrow("Images (${paths.size})")
    paths.forEachIndexed { i, path ->
        ImageRow(
            index = i,
            count = paths.size,
            thumb = thumbs[path]?.asImageBitmap(),
            onUp = { viewModel.move(i, -1) },
            onDown = { viewModel.move(i, 1) },
            onRemove = { viewModel.remove(i) },
        )
    }
    if (paths.size < CombineViewModel.MAX_IMAGES) {
        OutlinedButton(onClick = pick, shape = CircleShape, enabled = !importing) {
            Text(if (importing) "Adding..." else "Add images")
        }
    }

    Eyebrow("Direction")
    SegmentedTiles(
        options = listOf(
            SegmentOption(true, "Vertical", Icons.Default.ViewAgenda),
            SegmentOption(false, "Horizontal", Icons.Default.ViewWeek),
        ),
        selected = options.vertical,
        onSelect = viewModel::setVertical
    )

    // Slider keeps its own value while dragging so the preview isn't re-rendered on every tick.
    var spacingDrag by remember(options.spacingPx) { mutableFloatStateOf(options.spacingPx.toFloat()) }
    Eyebrow("Spacing  ${spacingDrag.toInt()} px")
    Slider(
        value = spacingDrag,
        onValueChange = { spacingDrag = it },
        onValueChangeFinished = { viewModel.setSpacing(spacingDrag.toInt()) },
        valueRange = 0f..CombineViewModel.MAX_SPACING.toFloat()
    )

    Eyebrow("Background")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        backgrounds.forEach { (color, label) ->
            FilterChip(
                selected = options.background == color,
                onClick = { viewModel.setBackground(color) },
                label = { Text(label) },
                leadingIcon = {
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(Color(color))
                            .border(1.dp, Lumen.tokens.hairline, CircleShape)
                    )
                }
            )
        }
    }

    Eyebrow("Align")
    SegmentedTiles(
        options = if (options.vertical) listOf(
            SegmentOption(Align.START, "Left", Icons.Default.AlignHorizontalLeft),
            SegmentOption(Align.CENTER, "Center", Icons.Default.AlignHorizontalCenter),
            SegmentOption(Align.END, "Right", Icons.Default.AlignHorizontalRight),
        ) else listOf(
            SegmentOption(Align.START, "Top", Icons.Default.AlignVerticalTop),
            SegmentOption(Align.CENTER, "Center", Icons.Default.AlignVerticalCenter),
            SegmentOption(Align.END, "Bottom", Icons.Default.AlignVerticalBottom),
        ),
        selected = options.align,
        onSelect = viewModel::setAlign
    )

    LumenCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (options.vertical) "Match widths" else "Match heights", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (options.vertical) "Scale every image to the widest one" else "Scale every image to the tallest one",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LumenSwitch(checked = options.matchSize, onCheckedChange = viewModel::setMatchSize)
        }
    }

    val ready = paths.size >= CombineViewModel.MIN_IMAGES && !importing
    PillCta(
        text = "Upload",
        icon = Icons.Default.CloudUpload,
        onClick = { viewModel.run(CombineAction.UPLOAD) },
        enabled = ready && busy == null,
        loading = busy == CombineAction.UPLOAD
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { viewModel.run(CombineAction.SHARE) },
            enabled = ready && busy == null,
            shape = CircleShape,
            modifier = Modifier.weight(1f)
        ) { Text(if (busy == CombineAction.SHARE) "Preparing..." else "Share") }
        OutlinedButton(
            onClick = onSave,
            enabled = ready && busy == null,
            shape = CircleShape,
            modifier = Modifier.weight(1f)
        ) { Text(if (busy == CombineAction.SAVE) "Saving..." else "Save") }
    }
    Spacer(Modifier.heightIn(min = 8.dp))
}

@Composable
private fun PreviewCard(preview: androidx.compose.ui.graphics.ImageBitmap?, rendering: Boolean, enough: Boolean) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 380.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Lumen.tokens.hairline, RoundedCornerShape(24.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            preview != null -> Image(
                bitmap = preview,
                contentDescription = "Combined preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 356.dp)
            )
            !enough -> Text("Add one more image to see the preview", style = monoStyle(12), color = MaterialTheme.colorScheme.onSurfaceVariant)
            !rendering -> Text("No preview", style = monoStyle(12), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (rendering) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun ImageRow(
    index: Int,
    count: Int,
    thumb: androidx.compose.ui.graphics.ImageBitmap?,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    LumenCard(contentPadding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Text(
                "Image ${index + 1}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).widthIn(min = 40.dp)
            )
            CircleIconButton(Icons.Default.KeyboardArrowUp, "Move image ${index + 1} up", onUp, enabled = index > 0)
            CircleIconButton(Icons.Default.KeyboardArrowDown, "Move image ${index + 1} down", onDown, enabled = index < count - 1)
            CircleIconButton(Icons.Default.Close, "Remove image ${index + 1}", onRemove)
        }
    }
}

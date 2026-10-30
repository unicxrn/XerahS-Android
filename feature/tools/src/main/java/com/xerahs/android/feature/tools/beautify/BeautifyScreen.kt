package com.xerahs.android.feature.tools.beautify

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Crop169
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlin.math.roundToInt

private val solids = listOf(
    0xFFFFFFFF.toInt() to "White",
    0xFF000000.toInt() to "Black",
)

@Composable
fun BeautifyScreen(
    onBack: () -> Unit,
    onUpload: (String) -> Unit,
    viewModel: BeautifyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val path by viewModel.path.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val rendering by viewModel.rendering.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val accent = MaterialTheme.colorScheme.primary
    LaunchedEffect(accent) { viewModel.setAccent(accent.toArgb()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.pick(it) }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val scope = rememberCoroutineScope()
    // Android 9 and older need storage access to write to Pictures.
    val save = rememberPicturesWriteAccess { granted ->
        if (granted) viewModel.run(BeautifyAction.SAVE)
        else scope.launch { snackbar.showSnackbar("Saving to Pictures needs storage access") }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is BeautifyEvent.Upload -> onUpload(e.path)
                is BeautifyEvent.Share -> runCatching { ToolOutput.share(context, e.file) }
                    .onFailure { snackbar.showSnackbar("Couldn't open the share sheet") }
                is BeautifyEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    Scaffold(
        topBar = { LumenTopBar(title = "Beautify", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (path == null) {
                Text(
                    "Pick an image to put it on a background with padding, rounded corners and a soft shadow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PillCta(text = "Choose image", onClick = pick, loading = importing)
            } else {
                PreviewCard(preview?.asImageBitmap(), rendering || importing)
                TextButton(onClick = pick, enabled = !importing, modifier = Modifier.align(Alignment.End)) {
                    Text("Change image")
                }
                Controls(options, accent, viewModel)

                val ready = !importing && busy == null
                PillCta(
                    text = "Upload",
                    icon = Icons.Default.CloudUpload,
                    onClick = { viewModel.run(BeautifyAction.UPLOAD) },
                    enabled = ready,
                    loading = busy == BeautifyAction.UPLOAD
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.run(BeautifyAction.SHARE) },
                        enabled = ready,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (busy == BeautifyAction.SHARE) "Preparing..." else "Share") }
                    OutlinedButton(
                        onClick = save,
                        enabled = ready,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (busy == BeautifyAction.SAVE) "Saving..." else "Save") }
                }
                Spacer(Modifier.heightIn(min = 8.dp))
            }
        }
    }
}

@Composable
private fun Controls(options: BeautifyOptions, accent: Color, viewModel: BeautifyViewModel) {
    PercentSlider("Padding", options.paddingPct, BeautifyViewModel.MAX_PADDING, viewModel::setPadding)
    PercentSlider("Corners", options.cornerRadiusPct, BeautifyViewModel.MAX_RADIUS, viewModel::setRadius)

    Eyebrow("Background")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GRADIENT_PRESETS.forEachIndexed { i, (label, from, to) ->
            BackgroundChip(label, options.background == Background.Gradient(i), Brush.linearGradient(listOf(Color(from), Color(to)))) {
                viewModel.setBackground(Background.Gradient(i))
            }
        }
        BackgroundChip("Accent", options.background == Background.Accent, Brush.linearGradient(listOf(accent, accent))) {
            viewModel.setBackground(Background.Accent)
        }
        solids.forEach { (argb, label) ->
            BackgroundChip(label, options.background == Background.Solid(argb), Brush.linearGradient(listOf(Color(argb), Color(argb)))) {
                viewModel.setBackground(Background.Solid(argb))
            }
        }
        BackgroundChip("Transparent", options.background == Background.Transparent, null) {
            viewModel.setBackground(Background.Transparent)
        }
    }

    LumenCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Shadow", style = MaterialTheme.typography.titleSmall)
                Text(
                    "A soft shadow under the image",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LumenSwitch(checked = options.shadow, onCheckedChange = viewModel::setShadow, contentDescription = "Shadow")
        }
    }

    Eyebrow("Aspect")
    SegmentedTiles(
        options = listOf(
            SegmentOption(Aspect.AUTO, "Auto", Icons.Default.AspectRatio),
            SegmentOption(Aspect.SQUARE, "1:1", Icons.Default.CropSquare),
            SegmentOption(Aspect.PORTRAIT_4_5, "4:5", Icons.Default.CropPortrait),
            SegmentOption(Aspect.WIDE_16_9, "16:9", Icons.Default.Crop169),
        ),
        selected = options.aspect,
        onSelect = viewModel::setAspect
    )
}

@Composable
private fun PercentSlider(label: String, value: Float, max: Float, onChange: (Float) -> Unit) {
    val pct = "${(value * 100).roundToInt()}%"
    Eyebrow("$label  $pct")
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = 0f..max,
        modifier = Modifier.semantics {
            contentDescription = label
            stateDescription = pct
        }
    )
}

@Composable
private fun BackgroundChip(label: String, selected: Boolean, swatch: Brush?, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            val m = Modifier.size(14.dp).clip(CircleShape)
            Box(
                (if (swatch != null) m.background(swatch) else m.drawBehind(checkerboard))
                    .border(1.dp, Lumen.tokens.hairline, CircleShape)
            )
        }
    )
}

// 3x3 light and dark grey squares, the usual sign for "see-through".
private val checkerboard: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
    val cell = size.width / 3f
    drawRect(Color(0xFFFFFFFF))
    for (row in 0 until 3) for (col in 0 until 3) {
        if ((row + col) % 2 == 1) drawRect(Color(0xFFBDBDC6), Offset(col * cell, row * cell), Size(cell, cell))
    }
}

@Composable
private fun PreviewCard(preview: ImageBitmap?, busy: Boolean) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 380.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Lumen.tokens.hairline, RoundedCornerShape(24.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = "Beautified preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 356.dp)
            )
        } else if (!busy) {
            Text("No preview", style = monoStyle(12), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (busy && preview == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

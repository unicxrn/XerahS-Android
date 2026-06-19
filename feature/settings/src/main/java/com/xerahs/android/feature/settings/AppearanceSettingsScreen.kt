package com.xerahs.android.feature.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard

/**
 * Built-in accent presets. The default (no custom seed) resolves to Signal Lime in the
 * theme engine; selecting any preset persists it as the active accent seed and re-themes
 * the whole app live via the contrast-safe color engine.
 */
private data class AccentPreset(val label: String, val argb: Int)

private val accentPresets = listOf(
    AccentPreset("Signal Lime", 0xFFB8F23A.toInt()),
    AccentPreset("Cyan", 0xFF2BE0E0.toInt()),
    AccentPreset("Violet", 0xFF6D3BF0.toInt()),
    AccentPreset("Blue", 0xFF3B82F6.toInt()),
    AccentPreset("Amber", 0xFFFFB020.toInt()),
    AccentPreset("Pink", 0xFFFF5C8A.toInt()),
    AccentPreset("Green", 0xFF34D17A.toInt()),
)

private val DEFAULT_ACCENT = accentPresets.first().argb

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateToThemeEditor: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCustomPicker by remember { mutableStateOf(false) }

    // The effective accent: an explicit seed if set, otherwise the default (Signal Lime).
    val effectiveAccent = uiState.currentAccentSeed ?: DEFAULT_ACCENT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // ---- Theme mode ----
            SectionHeader("Mode")
            SettingsGroupCard {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to "System",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark",
                    )
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = uiState.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                            label = { Text(label) }
                        )
                    }
                }
            }

            // ---- True black (only meaningful when dark surfaces can appear) ----
            AnimatedVisibility(
                visible = uiState.themeMode == ThemeMode.DARK || uiState.themeMode == ThemeMode.SYSTEM
            ) {
                SettingsGroupCard(modifier = Modifier.padding(top = 8.dp)) {
                    ListItem(
                        headlineContent = { Text("True black") },
                        supportingContent = { Text("Pure-black dark theme for AMOLED displays") },
                        leadingContent = {
                            Icon(Icons.Default.DarkMode, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.oledBlack,
                                onCheckedChange = { viewModel.setOledBlack(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setOledBlack(!uiState.oledBlack) }
                    )
                }
            }

            // ---- Accent presets + custom ----
            AnimatedVisibility(visible = !uiState.dynamicColor) {
                Column {
                    SectionHeader("Accent")
                    SettingsGroupCard {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            accentPresets.forEach { preset ->
                                val selected = effectiveAccent == preset.argb
                                AccentSwatch(
                                    color = Color(preset.argb),
                                    selected = selected,
                                    contentDescription = preset.label +
                                        if (selected) ", selected" else "",
                                    onClick = { viewModel.setAccentSeed(preset.argb) }
                                )
                            }

                            // Custom (eyedropper) swatch - opens an HSV picker.
                            val customSelected = accentPresets.none { it.argb == effectiveAccent }
                            CustomSwatch(
                                selected = customSelected,
                                selectedColor = if (customSelected) Color(effectiveAccent) else null,
                                onClick = { showCustomPicker = true }
                            )
                        }
                    }
                }
            }

            // ---- Live preview ----
            SectionHeader("Preview")
            ThemePreviewCard(modifier = Modifier.padding(horizontal = 16.dp))

            // ---- Dynamic color (Material You) ----
            SectionHeader("System colors")
            SettingsGroupCard {
                ListItem(
                    headlineContent = { Text("Use system colors (Material You)") },
                    supportingContent = {
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                "Overrides your accent with wallpaper-based colors"
                            else
                                "Requires Android 12 or higher"
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = uiState.dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        )
                    }
                )
            }
        }
    }

    if (showCustomPicker) {
        CustomAccentPickerDialog(
            initial = effectiveAccent,
            onDismiss = { showCustomPicker = false },
            onConfirm = { argb ->
                viewModel.setAccentSeed(argb)
                showCustomPicker = false
            }
        )
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = contentDescription, onClick = onClick)
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) else Modifier
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = color
        ) {
            if (selected) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomSwatch(
    selected: Boolean,
    selectedColor: Color?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = "Custom accent color", onClick = onClick)
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) else Modifier
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = selectedColor ?: MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Colorize,
                    contentDescription = "Pick a custom accent color",
                    tint = if (selectedColor != null) Color.Black
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = {}) { Text("Create") }

                // "Link copied" chip
                Surface(
                    shape = RoundedCornerShape(50),
                    color = cs.primary
                ) {
                    Text(
                        text = "Link copied",
                        color = cs.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Surface swatches
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewSwatch("Primary", cs.primary, cs.onPrimary)
                PreviewSwatch("Secondary", cs.secondaryContainer, cs.onSecondaryContainer)
                PreviewSwatch("Tertiary", cs.tertiaryContainer, cs.onTertiaryContainer)
            }
        }
    }
}

@Composable
private fun PreviewSwatch(label: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 44.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CustomAccentPickerDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val initialHsv = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1].coerceAtLeast(0.1f)) }
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.3f)) }

    val picked = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom accent") },
        text = {
            Column {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = Color(picked)
                ) {}

                Spacer(modifier = Modifier.height(16.dp))
                Text("Hue", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f
                )

                Text("Saturation", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0.1f..1f
                )

                Text("Brightness", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0.3f..1f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

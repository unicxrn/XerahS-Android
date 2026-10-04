package com.xerahs.android.feature.settings

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.ui.lumen.AccentGlow
import com.xerahs.android.core.ui.lumen.BezelCard
import com.xerahs.android.core.ui.lumen.Eyebrow
import com.xerahs.android.core.ui.lumen.IconTile
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.LumenCard
import com.xerahs.android.core.ui.lumen.LumenSwitch
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.SegmentOption
import com.xerahs.android.core.ui.lumen.SegmentedTiles
import com.xerahs.android.core.ui.lumen.monoStyle

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
        topBar = { LumenTopBar("Appearance", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            BezelCard { ThemePreviewCard() }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Theme")
                SegmentedTiles(
                    options = listOf(
                        SegmentOption(ThemeMode.SYSTEM, "System", Icons.Outlined.Monitor),
                        SegmentOption(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
                        SegmentOption(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
                    ),
                    selected = uiState.themeMode,
                    onSelect = viewModel::setThemeMode
                )
            }

            Column(
                modifier = Modifier.alpha(if (uiState.dynamicColor) 0.45f else 1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Eyebrow(if (uiState.dynamicColor) "Accent · overridden by system colors" else "Accent")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    accentPresets.forEach { preset ->
                        val selected = effectiveAccent == preset.argb
                        AccentSwatch(
                            color = Color(preset.argb),
                            selected = selected,
                            contentDescription = preset.label +
                                if (selected) ", selected" else "",
                            onClick = { viewModel.setAccentSeed(preset.argb) },
                            enabled = !uiState.dynamicColor
                        )
                    }

                    // Custom (eyedropper) swatch - opens an HSV picker.
                    val customSelected = accentPresets.none { it.argb == effectiveAccent }
                    CustomSwatch(
                        selected = customSelected,
                        selectedColor = if (customSelected) Color(effectiveAccent) else null,
                        onClick = { showCustomPicker = true },
                        enabled = !uiState.dynamicColor
                    )
                }
            }

            LumenCard {
                ToggleRow(
                    icon = Icons.Default.Palette,
                    title = "System colors",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        "Match your wallpaper (Material You)"
                    else
                        "Requires Android 12 or higher",
                    checked = uiState.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                )
                HorizontalDivider(color = Lumen.tokens.hairline)
                ToggleRow(
                    icon = Icons.Default.DarkMode,
                    title = "True black",
                    subtitle = "Applies in dark mode",
                    checked = uiState.oledBlack,
                    onCheckedChange = viewModel::setOledBlack,
                    enabled = uiState.themeMode != ThemeMode.LIGHT
                )
            }

            LumenCard(onClick = onNavigateToThemeEditor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(Icons.Default.Colorize, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Custom theme",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

/**
 * A 64dp toggle row: icon tile, title/subtitle, and a switch. The whole row toggles via a
 * single [Modifier.toggleable]; the switch itself is stripped of its own semantics
 * ([clearAndSetSemantics]) so TalkBack sees one control, not two.
 */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        LumenSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/** Live preview: brand mark, an accent "Upload" pill, a tint "Copy link" pill and a sample link. */
@Composable
private fun ThemePreviewCard() {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.background)
    ) {
        AccentGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-80).dp)
        )
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(cs.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.CenterFocusWeak,
                        contentDescription = null,
                        tint = cs.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text("XerahS", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(cs.primary)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Upload", color = cs.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(Lumen.tokens.tint)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Copy link", color = Lumen.tokens.ink, style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("is.gd/xR4m2", style = monoStyle(12), color = Lumen.tokens.ink)
        }
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

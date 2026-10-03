package com.xerahs.android.feature.annotation.effects

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.common.image.Corner
import com.xerahs.android.core.common.image.EffectSettings
import com.xerahs.android.core.common.image.ImageEffects
import com.xerahs.android.core.common.image.Watermark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun EffectsSheet(
    effects: EffectSettings,
    thumbnail: Bitmap,
    onChange: ((EffectSettings) -> EffectSettings) -> Unit,
    onPickBorderColor: () -> Unit,
    onReset: () -> Unit,
) {
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(effects, thumbnail) {
        delay(150)
        preview = withContext(Dispatchers.Default) { ImageEffects.apply(thumbnail, effects) }
    }
    Column(
        Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Effects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("Reset") }
        }
        preview?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp))
        }
        Slider3("Brightness", effects.brightness, -1f..1f) { v -> onChange { it.copy(brightness = v) } }
        Slider3("Contrast", effects.contrast, 0f..2f) { v -> onChange { it.copy(contrast = v) } }
        Slider3("Saturation", effects.saturation, 0f..2f) { v -> onChange { it.copy(saturation = v) } }
        Toggle("Grayscale", effects.grayscale) { v -> onChange { it.copy(grayscale = v) } }
        Toggle("Sepia", effects.sepia) { v -> onChange { it.copy(sepia = v) } }
        Toggle("Invert", effects.invert) { v -> onChange { it.copy(invert = v) } }
        Toggle("Drop shadow", effects.shadow) { v -> onChange { it.copy(shadow = v) } }
        Slider3("Rounded corners", effects.cornerRadius.toFloat(), 0f..96f) { v -> onChange { it.copy(cornerRadius = v.toInt()) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider3("Border", effects.borderWidth.toFloat(), 0f..64f, Modifier.weight(1f)) { v -> onChange { it.copy(borderWidth = v.toInt()) } }
            TextButton(onClick = onPickBorderColor) { Text("Colour") }
        }
        OutlinedTextField(
            value = effects.watermark?.text.orEmpty(),
            onValueChange = { text -> onChange { it.copy(watermark = if (text.isEmpty()) null else (it.watermark ?: Watermark("")).copy(text = text)) } },
            label = { Text("Watermark text") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        effects.watermark?.let { wm ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Corner.entries.forEach { c ->
                    FilterChip(selected = wm.corner == c, onClick = { onChange { it.copy(watermark = wm.copy(corner = c)) } },
                        label = { Text(c.name.lowercase().replace('_', ' ')) })
                }
            }
            Slider3("Watermark opacity", wm.opacity, 0.1f..1f) { v -> onChange { it.copy(watermark = wm.copy(opacity = v)) } }
        }
    }
}

@Composable
private fun Slider3(label: String, value: Float, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

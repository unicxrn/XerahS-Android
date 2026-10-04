package com.xerahs.android.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.ui.lumen.AccentGlow
import com.xerahs.android.core.ui.lumen.Eyebrow
import com.xerahs.android.core.ui.lumen.IconTile
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.LumenCard
import com.xerahs.android.core.ui.lumen.monoStyle

enum class ToolId(val title: String, val subtitle: String, val icon: ImageVector) {
    BATCH("Resize & convert", "Resize, change format, watermark", Icons.Default.PhotoSizeSelectLarge),
    HASH("Hash checker", "MD5, SHA-1, SHA-256", Icons.Default.Fingerprint),
    QR("QR decode", "Read a QR code from an image", Icons.Default.QrCodeScanner),
    COLOR("Color picker", "Pick colours from an image", Icons.Default.Colorize),
}

@Composable
fun ToolsScreen(onOpen: (ToolId) -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AccentGlow(Modifier.align(Alignment.TopEnd).offset(x = 140.dp, y = (-120).dp))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Tools", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(bottom = 8.dp))
            ToolHero(ToolId.BATCH) { onOpen(ToolId.BATCH) }
            ToolId.entries.filter { it != ToolId.BATCH }.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tool ->
                        LumenCard(Modifier.weight(1f).heightIn(min = 136.dp), contentPadding = 16.dp, onClick = { onOpen(tool) }) {
                            IconTile(tool.icon)
                            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                            Text(tool.title, style = MaterialTheme.typography.titleSmall)
                            Text(tool.subtitle, style = monoStyle(11), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolHero(tool: ToolId, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Lumen.tokens.hairline, RoundedCornerShape(30.dp)).padding(5.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 150.dp).clip(RoundedCornerShape(25.dp))
                .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.35f))))
                .clickable(role = Role.Button, onClick = onClick).padding(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Batch", color = on.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).background(on.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.NorthEast, null, tint = on, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(tool.title, style = MaterialTheme.typography.headlineSmall, color = on)
            Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.8f))
        }
    }
}

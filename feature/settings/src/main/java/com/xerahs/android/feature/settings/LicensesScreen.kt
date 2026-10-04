package com.xerahs.android.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.IconTile
import com.xerahs.android.core.ui.lumen.LumenCard
import com.xerahs.android.core.ui.lumen.monoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class FontLicense(
    val displayName: String,
    val assetPath: String
)

private val fontLicenses = listOf(
    FontLicense("Inter", "licenses/inter-OFL.txt"),
    FontLicense("Inter Tight", "licenses/intertight-OFL.txt"),
    FontLicense("JetBrains Mono", "licenses/jetbrainsmono-OFL.txt")
)

/** Settings row that opens [LicensesScreen]. Use on the Updates screen. */
@Composable
fun OpenSourceLicensesRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LumenCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(Icons.Filled.FontDownload, size = 40.dp)
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = "Open-source licenses",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Lists the bundled font licenses (SIL OFL 1.1). */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LumenTopBar(title = "Licenses", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            fontLicenses.forEach { font ->
                LicenseSection(font)
            }
        }
    }
}

@Composable
private fun LicenseSection(font: FontLicense) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember(font.assetPath) { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(font.assetPath, expanded) {
        if (expanded && text == null) {
            text = withContext(Dispatchers.IO) {
                runCatching {
                    reflowLicense(context.assets.open(font.assetPath).bufferedReader().use { it.readText() })
                }.getOrElse { "Couldn't load license text: ${it.message ?: "unknown error"}" }
            }
        }
    }

    LumenCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) "Collapse" else "Expand"
                    ) {
                        expanded = !expanded
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = font.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = text ?: "Loading…",
                        style = monoStyle(11),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// The OFL files are hard-wrapped at ~80 columns; join wrapped lines so the text flows on narrow screens.
private fun reflowLicense(raw: String): String =
    raw.replace("\r\n", "\n")
        .split(Regex("\n\\s*\n"))
        .joinToString("\n\n") { paragraph ->
            paragraph.lines().joinToString(" ") { it.trim() }.replace(Regex("-{10,}"), "")
                .replace(Regex(" {2,}"), " ").trim()
        }

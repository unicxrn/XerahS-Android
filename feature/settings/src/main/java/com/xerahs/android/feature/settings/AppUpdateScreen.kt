package com.xerahs.android.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.AnimatedListItem
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard
import com.xerahs.android.core.ui.ShimmerBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateScreen(
    onBack: () -> Unit,
    viewModel: AppUpdateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        ) {
            // Update status section
            SectionHeader("Current version")

            SettingsGroupCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "v${state.currentVersion}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (state.isChecking) {
                            Spacer(modifier = Modifier.width(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.error != null) {
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (state.updateAvailable && state.latestRelease != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.latestRelease!!.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "New version available",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        AnimatedVisibility(visible = state.isDownloading) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                    progress = { state.downloadProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Downloading... ${(state.downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        if (state.downloadError != null) {
                            Text(
                                text = state.downloadError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Button(
                            onClick = { viewModel.downloadAndInstall() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isDownloading
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state.isDownloading) "Downloading..." else "Download & install")
                        }
                    } else if (!state.isChecking && state.latestRelease != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "You're on the latest version",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FilledTonalButton(
                        onClick = { viewModel.checkForUpdate() },
                        enabled = !state.isChecking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for updates")
                    }
                }
            }

            // Changelog section
            SectionHeader("Changelog")

            if (state.isLoadingChangelog) {
                repeat(3) {
                    ShimmerBox(modifier = Modifier.padding(vertical = 4.dp))
                }
            } else if (state.allReleases.isEmpty()) {
                Text(
                    text = "No releases found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                state.allReleases.forEachIndexed { index, release ->
                    AnimatedListItem(index = index) {
                        SettingsGroupCard(
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = release.tagName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = formatDate(release.publishedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!release.body.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    MarkdownBody(release.body)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MarkdownBody(markdown: String) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val quoteBarColor = MaterialTheme.colorScheme.outlineVariant
    val bodyStyle = MaterialTheme.typography.bodySmall
    val headingStyle = MaterialTheme.typography.titleSmall
    val subheadingStyle = MaterialTheme.typography.labelLarge

    val lines = markdown.lines()
    var i = 0

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // Blank line — small spacer
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // H2 heading: ## Title
                trimmed.startsWith("## ") -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = trimmed.removePrefix("## "),
                        style = headingStyle,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                // H3 heading: ### Title
                trimmed.startsWith("### ") -> {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = trimmed.removePrefix("### "),
                        style = subheadingStyle,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Blockquote: > text
                trimmed.startsWith("> ") || trimmed == ">" -> {
                    val quoteText = trimmed.removePrefix("> ").removePrefix(">")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(quoteBarColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(quoteText),
                            style = bodyStyle,
                            fontStyle = FontStyle.Italic,
                            color = mutedColor
                        )
                    }
                }

                // Bullet point: - item or * item
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "\u2022",
                            style = bodyStyle,
                            color = accentColor,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(trimmed.drop(2)),
                            style = bodyStyle,
                            color = mutedColor
                        )
                    }
                }

                // Regular text
                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmed),
                        style = bodyStyle,
                        color = mutedColor
                    )
                }
            }
            i++
        }
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var pos = 0
        while (pos < text.length) {
            when {
                // Bold: **text**
                text.startsWith("**", pos) -> {
                    val end = text.indexOf("**", pos + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(pos + 2, end))
                        }
                        pos = end + 2
                    } else {
                        append(text[pos])
                        pos++
                    }
                }
                // Bold: __text__
                text.startsWith("__", pos) -> {
                    val end = text.indexOf("__", pos + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(pos + 2, end))
                        }
                        pos = end + 2
                    } else {
                        append(text[pos])
                        pos++
                    }
                }
                // Inline code: `code`
                text[pos] == '`' -> {
                    val end = text.indexOf('`', pos + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append(text.substring(pos + 1, end))
                        }
                        pos = end + 1
                    } else {
                        append(text[pos])
                        pos++
                    }
                }
                else -> {
                    append(text[pos])
                    pos++
                }
            }
        }
    }
}

private fun formatDate(isoDate: String): String {
    return try {
        val parts = isoDate.take(10).split("-")
        val months = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val month = months[(parts[1].toInt() - 1).coerceIn(0, 11)]
        val day = parts[2].toInt()
        val year = parts[0]
        "$month $day, $year"
    } catch (_: Exception) {
        isoDate.take(10)
    }
}

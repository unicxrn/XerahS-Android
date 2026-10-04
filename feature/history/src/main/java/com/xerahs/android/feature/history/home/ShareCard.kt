package com.xerahs.android.feature.history.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.common.formatSize
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.ui.lumen.AccentGlow
import com.xerahs.android.core.ui.lumen.BezelCard
import com.xerahs.android.core.ui.lumen.CircleIconButton
import com.xerahs.android.core.ui.lumen.Eyebrow
import com.xerahs.android.core.ui.lumen.HostChip
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.LumenCard
import com.xerahs.android.core.ui.lumen.PillCta
import com.xerahs.android.core.ui.lumen.SuccessBadge
import com.xerahs.android.core.ui.lumen.hostColor
import com.xerahs.android.core.ui.lumen.monoStyle
import com.xerahs.android.core.ui.lumen.relativeTime

/**
 * The "Link copied" moment - the emotional payoff after a successful share.
 * A calm confirmation surface: thumbnail, the copyable link, where it went, and the next actions.
 */
@Composable
fun ShareCard(
    item: HistoryItem,
    onShare: () -> Unit,
    onCopy: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    onShorten: () -> Unit = {},
    shortUrl: String? = null,
    isShortening: Boolean = false,
    canDeleteFromHost: Boolean = false,
    isDeleting: Boolean = false,
    onDeleteFromHost: () -> Unit = {}
) {
    val effectiveLink = shortUrl ?: item.url ?: item.filePath
    val context = LocalContext.current

    var showQr by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Animated scale for the check circle - scale in from 0.6 to 1.0 on first composition.
    val circleScale = remember { Animatable(if (reduceMotion) 1f else 0.6f) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            circleScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(modifier.fillMaxSize()) {
        AccentGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 120.dp, y = (-140).dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top row
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onDone)
            }

            // Badge and headline
            SuccessBadge(
                headline = "Uploaded.",
                subline = "Link ready.",
                scale = circleScale.value
            )

            // Link card
            BezelCard {
                Row(
                    modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Eyebrow(if (shortUrl != null) "Short link" else "Link")
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = effectiveLink,
                            style = monoStyle(20),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (shortUrl == null) {
                            if (isShortening) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Shortening…", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                TextButton(onClick = onShorten) {
                                    Text("Shorten")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(role = Role.Button) { onCopy(effectiveLink) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy link",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Action tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionTile(
                    icon = Icons.Default.Share,
                    label = "Share",
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
                if (item.url != null) {
                    ActionTile(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = "Open",
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effectiveLink)))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                ActionTile(
                    icon = Icons.Default.QrCode2,
                    label = "QR code",
                    onClick = { showQr = true },
                    modifier = Modifier.weight(1f)
                )
            }

            // Details
            Column {
                DetailRow(label = "Host") {
                    HostChip(item.uploadDestination.displayName, item.uploadDestination.hostColor())
                }
                HorizontalDivider(color = Lumen.tokens.hairline)
                DetailRow(label = "Size") {
                    Text(item.fileSize.formatSize(), style = monoStyle(12))
                }
                HorizontalDivider(color = Lumen.tokens.hairline)
                DetailRow(label = "Time") {
                    Text(relativeTime(item.timestamp), style = monoStyle(12))
                }
                if (item.deleteUrl != null) {
                    HorizontalDivider(color = Lumen.tokens.hairline)
                    DetailRow(label = "Delete URL") {
                        Text("saved", style = monoStyle(12))
                    }
                }
            }

            if (canDeleteFromHost) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    shape = CircleShape,
                    enabled = !isDeleting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text("Delete from host")
                }
            }

            Spacer(Modifier.size(4.dp))

            PillCta(text = "Done", onClick = onDone, icon = Icons.Default.Check, enabled = !isDeleting)
        }
    }

    if (showQr) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("QR Code") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QrImage(content = effectiveLink)
                    Text(
                        text = effectiveLink,
                        style = monoStyle(12),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQr = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete from host?") },
            text = {
                Text(
                    if (item.uploadDestination == UploadDestination.CUSTOM_HTTP) {
                        "Opens the host's deletion page."
                    } else {
                        "This removes the file from ${item.uploadDestination.displayName} and from your history."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDeleteFromHost() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LumenCard(
        modifier = modifier.height(72.dp),
        radius = 22.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = Lumen.tokens.ink)
            Spacer(Modifier.size(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

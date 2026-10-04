package com.xerahs.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.ui.lumen.AccentGlow
import com.xerahs.android.core.ui.lumen.IconTile
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.PillCta

private data class DestinationOption(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: UploadDestination
)

private val destinationOptions = listOf(
    DestinationOption(
        label = "Imgur",
        subtitle = "Quick, anonymous image hosting",
        icon = Icons.Default.Image,
        destination = UploadDestination.IMGUR
    ),
    DestinationOption(
        label = "S3 / R2",
        subtitle = "Your own object storage bucket",
        icon = Icons.Default.Storage,
        destination = UploadDestination.S3
    ),
    DestinationOption(
        label = "FTP / SFTP",
        subtitle = "Upload to your own server",
        icon = Icons.Default.Dns,
        destination = UploadDestination.FTP
    ),
    DestinationOption(
        label = "Custom uploader",
        subtitle = "ShareX-compatible (.sxcu) endpoint",
        icon = Icons.Default.Code,
        destination = UploadDestination.CUSTOM_HTTP
    ),
    DestinationOption(
        label = "Local",
        subtitle = "Save to this device only",
        icon = Icons.Default.Folder,
        destination = UploadDestination.LOCAL
    ),
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSelectDestination: (UploadDestination) -> Unit = {}
) {
    var selected by remember { mutableStateOf(UploadDestination.IMGUR) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AccentGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-100).dp)
        )
        OnboardingContent(
            selected = selected,
            onSelect = {
                selected = it
                onSelectDestination(it)
            },
            onComplete = {
                onSelectDestination(selected)
                onComplete()
            },
            onSkip = onComplete
        )
    }
}

@Composable
private fun OnboardingContent(
    selected: UploadDestination,
    onSelect: (UploadDestination) -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Subtle Skip at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = "Skip",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Wordmark, last syllable in the accent ink
            Text(
                text = buildAnnotatedString {
                    append("Xerah")
                    withStyle(SpanStyle(color = Lumen.tokens.ink)) { append("S") }
                },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Value proposition
            Text(
                text = "Turn any image into a shareable link in seconds.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Destination chooser heading
            Text(
                text = "Where should your images go?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            destinationOptions.forEach { option ->
                DestinationRow(
                    option = option,
                    isSelected = selected == option.destination,
                    onClick = { onSelect(option.destination) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Thumb-zone primary action
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            PillCta(text = "Get started", onClick = onComplete)
        }
    }
}

@Composable
private fun DestinationRow(
    option: DestinationOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Lumen.tokens.hairline
    val containerColor = if (isSelected) Lumen.tokens.tint else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(
            icon = option.icon,
            size = 40.dp,
            container = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = option.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

package com.xerahs.android.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.domain.model.ImageFormat
import com.xerahs.android.core.domain.model.UploadDestination
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Switch
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UploadSettingsScreen(
    onNavigateToImgurConfig: () -> Unit,
    onNavigateToS3Config: () -> Unit,
    onNavigateToFtpConfig: () -> Unit,
    onNavigateToCustomHttpConfig: () -> Unit,
    onNavigateToProfiles: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uploads") },
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
            // Destinations section — pick default + configure each service
            SectionHeader("Destinations")

            SettingsGroupCard {
                var dropdownExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.defaultDestination.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Default Upload Destination") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        UploadDestination.entries.forEach { dest ->
                            DropdownMenuItem(
                                text = { Text(dest.displayName) },
                                onClick = {
                                    viewModel.setDefaultDestination(dest)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Destination health check
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.destinationConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (uiState.destinationConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(start = 0.dp)
                    )
                    Text(
                        text = if (uiState.destinationConfigured) "Configured" else "Not configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.destinationConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                var destinationsExpanded by rememberSaveable { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Configure Destinations") },
                    supportingContent = { Text("Imgur, Amazon S3, FTP / SFTP, Custom HTTP") },
                    trailingContent = {
                        Icon(
                            if (destinationsExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (destinationsExpanded) "Collapse" else "Expand"
                        )
                    },
                    modifier = Modifier.clickable { destinationsExpanded = !destinationsExpanded }
                )

                AnimatedVisibility(
                    visible = destinationsExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        DestinationItem(
                            icon = Icons.Default.Image,
                            title = "Imgur",
                            subtitle = "Configure Imgur upload",
                            onClick = onNavigateToImgurConfig
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        DestinationItem(
                            icon = Icons.Default.Cloud,
                            title = "Amazon S3",
                            subtitle = "Configure S3 bucket upload",
                            onClick = onNavigateToS3Config
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        DestinationItem(
                            icon = Icons.Default.Dns,
                            title = "FTP / SFTP",
                            subtitle = "Configure FTP/SFTP upload",
                            onClick = onNavigateToFtpConfig
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        DestinationItem(
                            icon = Icons.Default.Http,
                            title = "Custom HTTP",
                            subtitle = "Configure custom HTTP endpoint",
                            onClick = onNavigateToCustomHttpConfig
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                ListItem(
                    headlineContent = { Text("Upload Profiles") },
                    supportingContent = { Text("Save named configurations for each destination") },
                    leadingContent = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToProfiles)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Image Processing section — quality and resize settings
            SectionHeader("Image Processing")

            SettingsGroupCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quality: ${uiState.imageQuality}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Slider(
                        value = uiState.imageQuality.toFloat(),
                        onValueChange = { viewModel.setImageQuality(it.toInt()) },
                        valueRange = 10f..100f,
                        steps = 17,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Lower quality = smaller file size. Only applies to JPEG compression.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Max Image Dimension",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val dimensions = listOf(
                            0 to "Original",
                            1920 to "1920px",
                            1440 to "1440px",
                            1080 to "1080px",
                            720 to "720px"
                        )
                        dimensions.forEach { (dim, label) ->
                            FilterChip(
                                selected = uiState.maxImageDimension == dim,
                                onClick = { viewModel.setMaxImageDimension(dim) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        text = "Resize images before uploading. 0 = no resize.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Output Format",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ImageFormat.entries.forEach { format ->
                            FilterChip(
                                selected = uiState.uploadFormat == format,
                                onClick = { viewModel.setUploadFormat(format) },
                                label = { Text(format.displayName) }
                            )
                        }
                    }
                    Text(
                        text = "Convert images before uploading. Original keeps the source format.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                ListItem(
                    headlineContent = { Text("Strip EXIF metadata") },
                    supportingContent = { Text("Remove GPS, camera info, and other metadata before uploading") },
                    trailingContent = {
                        Switch(
                            checked = uiState.stripExif,
                            onCheckedChange = { viewModel.setStripExif(it) }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Behavior section — naming, clipboard, and other general settings
            SectionHeader("Behavior")

            SettingsGroupCard {
                OutlinedTextField(
                    value = uiState.fileNamingPattern,
                    onValueChange = { viewModel.setFileNamingPattern(it) },
                    label = { Text("File Naming Pattern") },
                    supportingText = {
                        Text("Tokens: {original}, {date}, {time}, {timestamp}, {random}")
                    },
                    leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                ListItem(
                    headlineContent = { Text("Auto-copy URL after upload") },
                    supportingContent = { Text("Automatically copy the URL to clipboard when a single upload completes") },
                    trailingContent = {
                        Switch(
                            checked = uiState.autoCopyUrl,
                            onCheckedChange = { viewModel.setAutoCopyUrl(it) }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DestinationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

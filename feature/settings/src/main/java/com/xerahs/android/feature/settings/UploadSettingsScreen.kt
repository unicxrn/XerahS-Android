package com.xerahs.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.domain.model.UploadDestination
import androidx.compose.material3.Switch
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadSettingsScreen(
    onNavigateToImgurConfig: () -> Unit,
    onNavigateToS3Config: () -> Unit,
    onNavigateToFtpConfig: () -> Unit,
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
            // General section
            SectionHeader("General")

            SettingsGroupCard {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.defaultDestination.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Default Upload Destination") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        UploadDestination.entries.forEach { dest ->
                            DropdownMenuItem(
                                text = { Text(dest.displayName) },
                                onClick = {
                                    viewModel.setDefaultDestination(dest)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Behavior section
            SectionHeader("Behavior")

            SettingsGroupCard {
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

            Spacer(modifier = Modifier.height(8.dp))

            // Image Quality section
            SectionHeader("Image Quality")

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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Destination configuration section
            SectionHeader("Destination Configuration")

            SettingsGroupCard {
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

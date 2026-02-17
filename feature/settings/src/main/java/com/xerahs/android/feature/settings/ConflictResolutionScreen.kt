package com.xerahs.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolutionScreen(
    preview: ImportPreview,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    // Force recomposition counter when resolutions change
    var resolutionVersion by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Import") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Consume resolutionVersion to trigger recomposition
                    @Suppress("UNUSED_EXPRESSION")
                    resolutionVersion

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                preview.setAllResolutions(FieldResolution.KEEP_CURRENT)
                                resolutionVersion++
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Keep All Current")
                        }
                        OutlinedButton(
                            onClick = {
                                preview.setAllResolutions(FieldResolution.USE_IMPORTED)
                                resolutionVersion++
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Use All Imported")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onApply,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Import")
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                // Summary
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${preview.totalFields} fields found, ${preview.conflictCount} conflicts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            preview.sections.forEach { section ->
                item {
                    SectionHeader(section.title)
                }

                items(section.fields, key = { it.key }) { field ->
                    // Consume resolutionVersion to trigger recomposition
                    @Suppress("UNUSED_EXPRESSION")
                    resolutionVersion

                    ConflictFieldCard(
                        field = field,
                        onResolutionChanged = { resolutionVersion++ }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConflictFieldCard(
    field: ImportField,
    onResolutionChanged: () -> Unit
) {
    SettingsGroupCard(modifier = Modifier.padding(vertical = 2.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            if (field.hasConflict) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = field.resolution == FieldResolution.KEEP_CURRENT,
                        onClick = {
                            field.resolution = FieldResolution.KEEP_CURRENT
                            onResolutionChanged()
                        },
                        label = {
                            Column {
                                Text("Current", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = field.currentValue.ifBlank { "(empty)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = field.resolution == FieldResolution.USE_IMPORTED,
                        onClick = {
                            field.resolution = FieldResolution.USE_IMPORTED
                            onResolutionChanged()
                        },
                        label = {
                            Column {
                                Text("Imported", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = field.importedValue.ifBlank { "(empty)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // No conflict — values match, will be imported
                Text(
                    text = field.currentValue.ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

package com.xerahs.android.feature.settings.destinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard
import com.xerahs.android.core.ui.StatusBanner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S3ConfigScreen(
    onBack: () -> Unit,
    viewModel: S3ConfigViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isTesting by remember { mutableStateOf(false) }

    var accessKeyId by remember { mutableStateOf("") }
    var secretAccessKey by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }
    var bucket by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var acl by remember { mutableStateOf("") }
    var usePathStyle by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val config = viewModel.loadConfig()
        accessKeyId = config.accessKeyId
        secretAccessKey = config.secretAccessKey
        region = config.region
        bucket = config.bucket
        endpoint = config.endpoint ?: ""
        customUrl = config.customUrl ?: ""
        prefix = config.prefix
        acl = config.acl
        usePathStyle = config.usePathStyle
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Amazon S3 Configuration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Status indicator
            val isConfigured = accessKeyId.isNotBlank() && bucket.isNotBlank()
            StatusBanner(
                icon = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Info,
                title = if (isConfigured) "Configured" else "Not configured",
                subtitle = if (isConfigured) "Access key and bucket are set" else "Enter your S3 credentials and bucket",
                containerColor = if (isConfigured) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (isConfigured) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Authentication
            SectionHeader("Authentication")
            SettingsGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = accessKeyId, onValueChange = { accessKeyId = it },
                        label = { Text("Access Key ID") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = accessKeyId.isBlank(),
                        supportingText = if (accessKeyId.isBlank()) {{ Text("Required") }} else null
                    )
                    OutlinedTextField(
                        value = secretAccessKey, onValueChange = { secretAccessKey = it },
                        label = { Text("Secret Access Key") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = secretAccessKey.isBlank(),
                        supportingText = if (secretAccessKey.isBlank()) {{ Text("Required") }} else null
                    )
                }
            }

            // Bucket
            SectionHeader("Bucket")
            SettingsGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = region, onValueChange = { region = it },
                        label = { Text("Region") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = bucket, onValueChange = { bucket = it },
                        label = { Text("Bucket") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = bucket.isBlank(),
                        supportingText = if (bucket.isBlank()) {{ Text("Required") }} else null
                    )
                    OutlinedTextField(
                        value = endpoint, onValueChange = { endpoint = it },
                        label = { Text("S3 API Endpoint (optional)") },
                        supportingText = { Text("For MinIO, DigitalOcean Spaces, etc.") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }

            // URL & Path
            SectionHeader("URL & Path")
            SettingsGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customUrl, onValueChange = { customUrl = it },
                        label = { Text("Custom URL (optional)") },
                        supportingText = { Text("Public URL for images, e.g. https://i.example.com") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = prefix, onValueChange = { prefix = it },
                        label = { Text("Upload Path (optional)") },
                        supportingText = { Text("Subdirectory in bucket, e.g. screenshots/2026") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = acl, onValueChange = { acl = it },
                        label = { Text("ACL") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }

            // Advanced
            SectionHeader("Advanced")
            SettingsGroupCard {
                ListItem(
                    headlineContent = { Text("Path-style URLs") },
                    supportingContent = { Text("Use path-style instead of virtual-hosted") },
                    trailingContent = {
                        Switch(checked = usePathStyle, onCheckedChange = { usePathStyle = it })
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val canSave = accessKeyId.isNotBlank() && secretAccessKey.isNotBlank() && bucket.isNotBlank()
            Button(
                onClick = {
                    coroutineScope.launch {
                        viewModel.saveConfig(
                            accessKeyId, secretAccessKey, region, bucket,
                            endpoint.ifEmpty { null }, customUrl.ifEmpty { null },
                            prefix, acl, usePathStyle
                        )
                        snackbarHostState.showSnackbar("S3 settings saved")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                shape = MaterialTheme.shapes.large,
                enabled = canSave
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    isTesting = true
                    coroutineScope.launch {
                        val result = viewModel.testConnection(
                            accessKeyId, secretAccessKey, region, bucket,
                            endpoint.ifEmpty { null }, usePathStyle
                        )
                        isTesting = false
                        snackbarHostState.showSnackbar(result)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                shape = MaterialTheme.shapes.large,
                enabled = canSave && !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Test Connection")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

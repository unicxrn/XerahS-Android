package com.xerahs.android.feature.settings.destinations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S3ConfigScreen(
    onBack: () -> Unit,
    viewModel: S3ConfigViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = accessKeyId, onValueChange = { accessKeyId = it },
                label = { Text("Access Key ID") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = secretAccessKey, onValueChange = { secretAccessKey = it },
                label = { Text("Secret Access Key") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = region, onValueChange = { region = it },
                label = { Text("Region") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = bucket, onValueChange = { bucket = it },
                label = { Text("Bucket") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = endpoint, onValueChange = { endpoint = it },
                label = { Text("S3 API Endpoint (optional)") },
                supportingText = { Text("For MinIO, DigitalOcean Spaces, etc.") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = customUrl, onValueChange = { customUrl = it },
                label = { Text("Custom URL (optional)") },
                supportingText = { Text("Public URL for images, e.g. https://i.example.com") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = prefix, onValueChange = { prefix = it },
                label = { Text("Upload Path (optional)") },
                supportingText = { Text("Subdirectory in bucket, e.g. screenshots/2026") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = acl, onValueChange = { acl = it },
                label = { Text("ACL") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.material3.ListItem(
                headlineContent = { Text("Path-style URLs") },
                supportingContent = { Text("Use path-style instead of virtual-hosted") },
                trailingContent = {
                    Switch(checked = usePathStyle, onCheckedChange = { usePathStyle = it })
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            ElevatedButton(
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

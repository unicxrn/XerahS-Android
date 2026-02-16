package com.xerahs.android.feature.settings.destinations

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard
import com.xerahs.android.core.ui.StatusBanner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHttpConfigScreen(
    onBack: () -> Unit,
    viewModel: CustomHttpConfigViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isTesting by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("POST") }
    var responseUrlJsonPath by remember { mutableStateOf("url") }
    var formFieldName by remember { mutableStateOf("file") }

    val headerKeys = remember { mutableStateListOf<String>() }
    val headerValues = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        val config = viewModel.loadConfig()
        url = config.url
        method = config.method
        responseUrlJsonPath = config.responseUrlJsonPath
        formFieldName = config.formFieldName
        headerKeys.clear()
        headerValues.clear()
        config.headers.forEach { (k, v) ->
            headerKeys.add(k)
            headerValues.add(v)
        }
    }

    fun headersMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in headerKeys.indices) {
            val k = headerKeys[i].trim()
            if (k.isNotEmpty()) {
                map[k] = headerValues[i]
            }
        }
        return map
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom HTTP Configuration") },
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
            val isConfigured = url.isNotBlank()
            StatusBanner(
                icon = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Info,
                title = if (isConfigured) "Configured" else "Not configured",
                subtitle = if (isConfigured) "Endpoint URL is set" else "Enter your HTTP endpoint URL",
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

            SectionHeader("Endpoint")
            SettingsGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("URL") },
                        supportingText = { Text("The full URL to POST/PUT the file to") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = url.isBlank(),
                    )

                    var methodExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = methodExpanded,
                        onExpandedChange = { methodExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = method,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("HTTP Method") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = methodExpanded,
                            onDismissRequest = { methodExpanded = false }
                        ) {
                            listOf("POST", "PUT").forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = { method = m; methodExpanded = false }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = formFieldName, onValueChange = { formFieldName = it },
                        label = { Text("Form Field Name") },
                        supportingText = { Text("Multipart form field name for the file (default: file)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }

            SectionHeader("Response Parsing")
            SettingsGroupCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = responseUrlJsonPath, onValueChange = { responseUrlJsonPath = it },
                        label = { Text("Response URL JSON Path") },
                        supportingText = { Text("Dot-separated path to URL in JSON response, e.g. data.url") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }

            SectionHeader("Headers")
            SettingsGroupCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    for (i in headerKeys.indices) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = headerKeys[i],
                                onValueChange = { headerKeys[i] = it },
                                label = { Text("Key") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = headerValues[i],
                                onValueChange = { headerValues[i] = it },
                                label = { Text("Value") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    headerKeys.removeAt(i)
                                    headerValues.removeAt(i)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            headerKeys.add("")
                            headerValues.add("")
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Header")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val canSave = url.isNotBlank()
            Button(
                onClick = {
                    coroutineScope.launch {
                        viewModel.saveConfig(url, method, headersMap(), responseUrlJsonPath, formFieldName)
                        snackbarHostState.showSnackbar("Custom HTTP settings saved")
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
                        val result = viewModel.testConnection(url, method, headersMap())
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

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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard
import com.xerahs.android.core.ui.StatusBanner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpConfigScreen(
    onBack: () -> Unit,
    viewModel: FtpConfigViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var isTesting by remember { mutableStateOf(false) }

    // FTP fields
    var ftpHost by remember { mutableStateOf("") }
    var ftpPort by remember { mutableStateOf("21") }
    var ftpUsername by remember { mutableStateOf("") }
    var ftpPassword by remember { mutableStateOf("") }
    var ftpRemotePath by remember { mutableStateOf("/") }
    var ftpUseFtps by remember { mutableStateOf(false) }
    var ftpUsePassive by remember { mutableStateOf(true) }
    var ftpHttpUrl by remember { mutableStateOf("") }

    // SFTP fields
    var sftpHost by remember { mutableStateOf("") }
    var sftpPort by remember { mutableStateOf("22") }
    var sftpUsername by remember { mutableStateOf("") }
    var sftpPassword by remember { mutableStateOf("") }
    var sftpKeyPath by remember { mutableStateOf("") }
    var sftpRemotePath by remember { mutableStateOf("/") }
    var sftpHttpUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val ftpConfig = viewModel.loadFtpConfig()
        ftpHost = ftpConfig.host
        ftpPort = ftpConfig.port.toString()
        ftpUsername = ftpConfig.username
        ftpPassword = ftpConfig.password
        ftpRemotePath = ftpConfig.remotePath
        ftpUseFtps = ftpConfig.useFtps
        ftpUsePassive = ftpConfig.usePassiveMode
        ftpHttpUrl = ftpConfig.httpUrl

        val sftpConfig = viewModel.loadSftpConfig()
        sftpHost = sftpConfig.host
        sftpPort = sftpConfig.port.toString()
        sftpUsername = sftpConfig.username
        sftpPassword = sftpConfig.password
        sftpKeyPath = sftpConfig.keyPath ?: ""
        sftpRemotePath = sftpConfig.remotePath
        sftpHttpUrl = sftpConfig.httpUrl
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FTP / SFTP Configuration") },
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
        ) {
            // Status indicator
            val isConfigured = if (selectedTab == 0) ftpHost.isNotBlank() else sftpHost.isNotBlank()
            StatusBanner(
                icon = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Info,
                title = if (isConfigured) "Configured" else "Not configured",
                subtitle = if (isConfigured) "Host is set" else "Enter connection details",
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

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("FTP", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("SFTP", modifier = Modifier.padding(16.dp))
                }
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (selectedTab == 0) {
                    // FTP configuration
                    SectionHeader("Connection")
                    SettingsGroupCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = ftpHost, onValueChange = { ftpHost = it },
                                label = { Text("Host") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                isError = ftpHost.isBlank(),
                                supportingText = if (ftpHost.isBlank()) {{ Text("Required") }} else null
                            )
                            OutlinedTextField(
                                value = ftpPort, onValueChange = { ftpPort = it },
                                label = { Text("Port") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                        }
                    }

                    SectionHeader("Authentication")
                    SettingsGroupCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = ftpUsername, onValueChange = { ftpUsername = it },
                                label = { Text("Username") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            OutlinedTextField(
                                value = ftpPassword, onValueChange = { ftpPassword = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                        }
                    }

                    SectionHeader("Paths")
                    SettingsGroupCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = ftpRemotePath, onValueChange = { ftpRemotePath = it },
                                label = { Text("Remote Path") },
                                supportingText = { Text("Tokens: {yyyy}, {yy}, {MM}, {dd}, {month}\ne.g. /uploads/{yyyy}/{MM}") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            OutlinedTextField(
                                value = ftpHttpUrl, onValueChange = { ftpHttpUrl = it },
                                label = { Text("HTTP URL (for link generation)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                        }
                    }

                    SectionHeader("Options")
                    SettingsGroupCard {
                        ListItem(
                            headlineContent = { Text("Use FTPS") },
                            trailingContent = {
                                Switch(checked = ftpUseFtps, onCheckedChange = { ftpUseFtps = it })
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Passive Mode") },
                            trailingContent = {
                                Switch(checked = ftpUsePassive, onCheckedChange = { ftpUsePassive = it })
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.saveFtpConfig(
                                    ftpHost, ftpPort.toIntOrNull() ?: 21,
                                    ftpUsername, ftpPassword, ftpRemotePath,
                                    ftpUseFtps, ftpUsePassive, ftpHttpUrl
                                )
                                snackbarHostState.showSnackbar("FTP settings saved")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.large,
                        enabled = ftpHost.isNotBlank()
                    ) { Text("Save FTP Settings") }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            coroutineScope.launch {
                                val result = viewModel.testFtpConnection(
                                    ftpHost, ftpPort.toIntOrNull() ?: 21,
                                    ftpUsername, ftpPassword, ftpUseFtps
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
                        enabled = ftpHost.isNotBlank() && !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }

                } else {
                    // SFTP configuration
                    SectionHeader("Connection")
                    SettingsGroupCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = sftpHost, onValueChange = { sftpHost = it },
                                label = { Text("Host") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                isError = sftpHost.isBlank(),
                                supportingText = if (sftpHost.isBlank()) {{ Text("Required") }} else null
                            )
                            OutlinedTextField(
                                value = sftpPort, onValueChange = { sftpPort = it },
                                label = { Text("Port") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                        }
                    }

                    SectionHeader("Authentication")
                    SettingsGroupCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = sftpUsername, onValueChange = { sftpUsername = it },
                                label = { Text("Username") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            OutlinedTextField(
                                value = sftpPassword, onValueChange = { sftpPassword = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            OutlinedTextField(
                                value = sftpKeyPath, onValueChange = { sftpKeyPath = it },
                                label = { Text("SSH Key Path (optional)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                        }
                    }

                    SectionHeader("Paths")
                    SettingsGroupCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = sftpRemotePath, onValueChange = { sftpRemotePath = it },
                                label = { Text("Remote Path") },
                                supportingText = { Text("Tokens: {yyyy}, {yy}, {MM}, {dd}, {month}\ne.g. /uploads/{yyyy}/{MM}") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            OutlinedTextField(
                                value = sftpHttpUrl, onValueChange = { sftpHttpUrl = it },
                                label = { Text("HTTP URL (for link generation)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.saveSftpConfig(
                                    sftpHost, sftpPort.toIntOrNull() ?: 22,
                                    sftpUsername, sftpPassword,
                                    sftpKeyPath.ifEmpty { null },
                                    sftpRemotePath, sftpHttpUrl
                                )
                                snackbarHostState.showSnackbar("SFTP settings saved")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.large,
                        enabled = sftpHost.isNotBlank()
                    ) { Text("Save SFTP Settings") }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            coroutineScope.launch {
                                val result = viewModel.testSftpConnection(
                                    sftpHost, sftpPort.toIntOrNull() ?: 22,
                                    sftpUsername, sftpPassword,
                                    sftpKeyPath.ifEmpty { null }
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
                        enabled = sftpHost.isNotBlank() && !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

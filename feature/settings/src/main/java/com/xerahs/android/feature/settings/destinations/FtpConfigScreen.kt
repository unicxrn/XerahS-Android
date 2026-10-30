package com.xerahs.android.feature.settings.destinations

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard
import com.xerahs.android.core.ui.StatusBanner
import com.xerahs.android.core.ui.lumen.LumenSwitch
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.PillCta
import com.xerahs.android.core.ui.lumen.monoStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpConfigScreen(
    onBack: () -> Unit,
    viewModel: FtpConfigViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(viewModel.initialTab) }
    var isTesting by remember { mutableStateOf(false) }
    val savedHostKeys by viewModel.savedHostKeys.collectAsState()

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
    var sftpKeyPassphrase by remember { mutableStateOf("") }
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
        sftpKeyPassphrase = sftpConfig.keyPassphrase ?: ""
        sftpRemotePath = sftpConfig.remotePath
        sftpHttpUrl = sftpConfig.httpUrl
    }

    Scaffold(
        topBar = { LumenTopBar(title = "FTP / SFTP Configuration", onBack = onBack) },
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
                                shape = RoundedCornerShape(18.dp),
                                isError = ftpHost.isBlank(),
                                supportingText = if (ftpHost.isBlank()) {{ Text("Required") }} else null
                            )
                            OutlinedTextField(
                                value = ftpPort, onValueChange = { ftpPort = it },
                                label = { Text("Port") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp)
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
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )
                            OutlinedTextField(
                                value = ftpPassword, onValueChange = { ftpPassword = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp),
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
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )
                            OutlinedTextField(
                                value = ftpHttpUrl, onValueChange = { ftpHttpUrl = it },
                                label = { Text("HTTP URL (for link generation)") },
                                supportingText = { Text("Web address of Remote Path up to the first token. Date folders are added to each link.") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    }

                    SectionHeader("Options")
                    SettingsGroupCard {
                        ListItem(
                            headlineContent = { Text("Use FTPS") },
                            trailingContent = {
                                LumenSwitch(checked = ftpUseFtps, onCheckedChange = { ftpUseFtps = it })
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        ListItem(
                            headlineContent = { Text("Passive Mode") },
                            trailingContent = {
                                LumenSwitch(checked = ftpUsePassive, onCheckedChange = { ftpUsePassive = it })
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    PillCta(
                        text = "Save FTP Settings",
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
                            .padding(horizontal = 16.dp),
                        enabled = ftpHost.isNotBlank()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            coroutineScope.launch {
                                val result = viewModel.testFtpConnection(
                                    ftpHost, ftpPort.toIntOrNull() ?: 21,
                                    ftpUsername, ftpPassword, ftpUseFtps, ftpUsePassive
                                )
                                isTesting = false
                                snackbarHostState.showSnackbar(result)
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(48.dp),
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
                                shape = RoundedCornerShape(18.dp),
                                isError = sftpHost.isBlank(),
                                supportingText = if (sftpHost.isBlank()) {{ Text("Required") }} else null
                            )
                            OutlinedTextField(
                                value = sftpPort, onValueChange = { sftpPort = it },
                                label = { Text("Port") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp)
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
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )
                            OutlinedTextField(
                                value = sftpPassword, onValueChange = { sftpPassword = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                visualTransformation = PasswordVisualTransformation()
                            )
                            OutlinedTextField(
                                value = sftpKeyPath, onValueChange = { sftpKeyPath = it },
                                label = { Text("SSH Key Path (optional)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )
                            OutlinedTextField(
                                value = sftpKeyPassphrase, onValueChange = { sftpKeyPassphrase = it },
                                label = { Text("Key Passphrase (optional)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp),
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
                                value = sftpRemotePath, onValueChange = { sftpRemotePath = it },
                                label = { Text("Remote Path") },
                                supportingText = { Text("Tokens: {yyyy}, {yy}, {MM}, {dd}, {month}\ne.g. /uploads/{yyyy}/{MM}") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )
                            OutlinedTextField(
                                value = sftpHttpUrl, onValueChange = { sftpHttpUrl = it },
                                label = { Text("HTTP URL (for link generation)") },
                                supportingText = { Text("Web address of Remote Path up to the first token. Date folders are added to each link.") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    PillCta(
                        text = "Save SFTP Settings",
                        onClick = {
                            coroutineScope.launch {
                                viewModel.saveSftpConfig(
                                    sftpHost, sftpPort.toIntOrNull() ?: 22,
                                    sftpUsername, sftpPassword,
                                    sftpKeyPath.ifEmpty { null },
                                    sftpKeyPassphrase.ifEmpty { null },
                                    sftpRemotePath, sftpHttpUrl
                                )
                                snackbarHostState.showSnackbar("SFTP settings saved")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        enabled = sftpHost.isNotBlank()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            coroutineScope.launch {
                                val result = viewModel.testSftpConnection(
                                    sftpHost, sftpPort.toIntOrNull() ?: 22,
                                    sftpUsername, sftpPassword,
                                    sftpKeyPath.ifEmpty { null },
                                    sftpKeyPassphrase.ifEmpty { null }
                                )
                                isTesting = false
                                snackbarHostState.showSnackbar(result, duration = SnackbarDuration.Long)
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(48.dp),
                        enabled = sftpHost.isNotBlank() && !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val had = viewModel.forgetSftpHostKey(sftpHost, sftpPort.toIntOrNull() ?: 22)
                                snackbarHostState.showSnackbar(
                                    if (had) "Saved host key forgotten. The next connection will save the new one."
                                    else "No saved host key for this server"
                                )
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        // A fixed red border would make the disabled button look active.
                        border = BorderStroke(
                            1.dp,
                            if (sftpHost.isNotBlank()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(48.dp),
                        enabled = sftpHost.isNotBlank()
                    ) {
                        Text("Forget saved host key")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    SavedHostKeys(
                        keys = savedHostKeys,
                        onForget = { key ->
                            viewModel.forgetHostKey(key.hostPort)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Forgot the key for ${key.hostPort}. The next connection saves the new one.")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** Pinned SFTP host keys for every server, including the ones only profiles use. */
@Composable
private fun SavedHostKeys(keys: List<SavedHostKey>, onForget: (SavedHostKey) -> Unit) {
    SectionHeader("Saved host keys")
    SettingsGroupCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (keys.isEmpty()) {
                Text(
                    "No saved host keys yet. A server's key is saved the first time you connect to it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            keys.forEach { key ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(key.hostPort, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            key.fingerprint,
                            style = monoStyle(11),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { onForget(key) },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text("Forget")
                    }
                }
            }
        }
    }
}

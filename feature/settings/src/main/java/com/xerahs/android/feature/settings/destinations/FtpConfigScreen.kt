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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("FTP", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("SFTP", modifier = Modifier.padding(16.dp))
                }
            }

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (selectedTab == 0) {
                    // FTP configuration
                    OutlinedTextField(
                        value = ftpHost, onValueChange = { ftpHost = it },
                        label = { Text("Host") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ftpPort, onValueChange = { ftpPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ftpUsername, onValueChange = { ftpUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ftpPassword, onValueChange = { ftpPassword = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ftpRemotePath, onValueChange = { ftpRemotePath = it },
                        label = { Text("Remote Path") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ftpHttpUrl, onValueChange = { ftpHttpUrl = it },
                        label = { Text("HTTP URL (for link generation)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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

                    Spacer(modifier = Modifier.height(24.dp))
                    ElevatedButton(
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
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save FTP Settings") }

                } else {
                    // SFTP configuration
                    OutlinedTextField(
                        value = sftpHost, onValueChange = { sftpHost = it },
                        label = { Text("Host") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sftpPort, onValueChange = { sftpPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sftpUsername, onValueChange = { sftpUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sftpPassword, onValueChange = { sftpPassword = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sftpKeyPath, onValueChange = { sftpKeyPath = it },
                        label = { Text("SSH Key Path (optional)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sftpRemotePath, onValueChange = { sftpRemotePath = it },
                        label = { Text("Remote Path") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sftpHttpUrl, onValueChange = { sftpHttpUrl = it },
                        label = { Text("HTTP URL (for link generation)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    ElevatedButton(
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
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save SFTP Settings") }
                }
            }
        }
    }
}

package com.xerahs.android.feature.settings.destinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard
import com.xerahs.android.core.ui.StatusBanner
import com.xerahs.android.core.ui.lumen.LumenSwitch
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.PillCta
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImgurConfigScreen(
    onBack: () -> Unit,
    viewModel: ImgurConfigViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var useAnonymous by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val config = viewModel.loadConfig()
        clientId = config.clientId
        clientSecret = config.clientSecret
        useAnonymous = config.useAnonymous
    }

    Scaffold(
        topBar = { LumenTopBar(title = "Imgur Configuration", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Status indicator
            val isConfigured = clientId.isNotBlank()
            StatusBanner(
                icon = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Info,
                title = if (isConfigured) "Configured" else "Not configured",
                subtitle = if (isConfigured) "Client ID is set" else "Enter your Imgur API credentials",
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

            // Account Type
            SectionHeader("Account Type")
            SettingsGroupCard {
                ListItem(
                    headlineContent = { Text("Anonymous Upload") },
                    supportingContent = { Text("Upload without an Imgur account") },
                    trailingContent = {
                        LumenSwitch(checked = useAnonymous, onCheckedChange = { useAnonymous = it })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // API Credentials
            SectionHeader("API Credentials")
            SettingsGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text("Client ID") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        isError = clientId.isBlank(),
                        supportingText = if (clientId.isBlank()) {{ Text("Required") }} else null
                    )
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it },
                        label = { Text("Client Secret") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        isError = !useAnonymous && clientSecret.isBlank(),
                        supportingText = if (!useAnonymous && clientSecret.isBlank()) {{ Text("Required for authenticated upload") }} else null
                    )

                    if (!useAnonymous) {
                        Text(
                            text = "OAuth PIN-based flow: After saving, visit the Imgur authorization URL to get a PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val canSave = clientId.isNotBlank() && (useAnonymous || clientSecret.isNotBlank())
            PillCta(
                text = "Save",
                onClick = {
                    coroutineScope.launch {
                        viewModel.saveConfig(clientId, clientSecret, useAnonymous)
                        snackbarHostState.showSnackbar("Imgur settings saved")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                enabled = canSave
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

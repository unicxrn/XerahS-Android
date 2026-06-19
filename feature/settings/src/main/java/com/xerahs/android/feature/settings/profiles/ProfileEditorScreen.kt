package com.xerahs.android.feature.settings.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.domain.model.UploadDestination

// Destination data-dot color reused from management screen logic
private fun destDotColor(dest: UploadDestination): Color = when (dest) {
    UploadDestination.IMGUR -> Color(0xFF1BB76E)
    UploadDestination.S3 -> Color(0xFFFF9900)
    UploadDestination.FTP -> Color(0xFF2E86DE)
    UploadDestination.SFTP -> Color(0xFF6B7C93)
    UploadDestination.CUSTOM_HTTP -> Color(0xFF8E8E93)
    UploadDestination.LOCAL -> Color(0xFF8E8E93)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    onBack: () -> Unit,
    viewModel: ProfileManagementViewModel
) {
    val state by viewModel.editorState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit Profile" else "New Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // --- Profile Name ---
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateEditorName(it) },
                label = { Text("Profile Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- Destination picker ---
            var destExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = destExpanded,
                onExpandedChange = { destExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = state.destination.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Destination") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(destDotColor(state.destination))
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = destExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    enabled = !state.isEditing
                )
                ExposedDropdownMenu(
                    expanded = destExpanded,
                    onDismissRequest = { destExpanded = false }
                ) {
                    UploadDestination.entries.filter { it != UploadDestination.LOCAL }.forEach { dest ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(destDotColor(dest))
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(dest.displayName)
                                }
                            },
                            onClick = {
                                viewModel.updateEditorDestination(dest)
                                destExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- Set as Default ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Set as Default",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Use this profile by default for ${state.destination.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.isDefault,
                    onCheckedChange = { viewModel.updateEditorDefault(it) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // --- Section label ---
            Text(
                text = "${state.destination.displayName} Configuration",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // --- Destination-specific fields ---
            when (state.destination) {
                UploadDestination.IMGUR -> ImgurFields(state, viewModel)
                UploadDestination.S3 -> S3Fields(state, viewModel)
                UploadDestination.FTP -> FtpFields(state, viewModel)
                UploadDestination.SFTP -> SftpFields(state, viewModel)
                UploadDestination.CUSTOM_HTTP -> CustomHttpFields(state, viewModel)
                UploadDestination.LOCAL -> {}
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Save button (primary, full-width) ---
            Button(
                onClick = { viewModel.saveProfile(onComplete = onBack) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Save Profile",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ImgurFields(state: ProfileEditorUiState, viewModel: ProfileManagementViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Anonymous Upload",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = state.imgurUseAnonymous,
            onCheckedChange = { viewModel.updateImgurUseAnonymous(it) }
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.imgurClientId,
        onValueChange = { viewModel.updateImgurClientId(it) },
        label = { Text("Client ID") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.imgurClientSecret,
        onValueChange = { viewModel.updateImgurClientSecret(it) },
        label = { Text("Client Secret") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun S3Fields(state: ProfileEditorUiState, viewModel: ProfileManagementViewModel) {
    OutlinedTextField(
        value = state.s3AccessKeyId,
        onValueChange = { viewModel.updateS3AccessKeyId(it) },
        label = { Text("Access Key ID") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3SecretAccessKey,
        onValueChange = { viewModel.updateS3SecretAccessKey(it) },
        label = { Text("Secret Access Key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3Region,
        onValueChange = { viewModel.updateS3Region(it) },
        label = { Text("Region") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3Bucket,
        onValueChange = { viewModel.updateS3Bucket(it) },
        label = { Text("Bucket") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3Endpoint,
        onValueChange = { viewModel.updateS3Endpoint(it) },
        label = { Text("Custom Endpoint (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3CustomUrl,
        onValueChange = { viewModel.updateS3CustomUrl(it) },
        label = { Text("Custom URL (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3Prefix,
        onValueChange = { viewModel.updateS3Prefix(it) },
        label = { Text("Key Prefix") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3Acl,
        onValueChange = { viewModel.updateS3Acl(it) },
        label = { Text("ACL (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Path-style access",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = state.s3UsePathStyle,
            onCheckedChange = { viewModel.updateS3UsePathStyle(it) }
        )
    }
}

@Composable
private fun FtpFields(state: ProfileEditorUiState, viewModel: ProfileManagementViewModel) {
    OutlinedTextField(
        value = state.ftpHost,
        onValueChange = { viewModel.updateFtpHost(it) },
        label = { Text("Host") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ftpPort,
        onValueChange = { viewModel.updateFtpPort(it) },
        label = { Text("Port") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ftpUsername,
        onValueChange = { viewModel.updateFtpUsername(it) },
        label = { Text("Username") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ftpPassword,
        onValueChange = { viewModel.updateFtpPassword(it) },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ftpRemotePath,
        onValueChange = { viewModel.updateFtpRemotePath(it) },
        label = { Text("Remote Path") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ftpHttpUrl,
        onValueChange = { viewModel.updateFtpHttpUrl(it) },
        label = { Text("HTTP URL Prefix") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Use FTPS",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = state.ftpUseFtps,
            onCheckedChange = { viewModel.updateFtpUseFtps(it) }
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Passive Mode",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = state.ftpUsePassive,
            onCheckedChange = { viewModel.updateFtpUsePassive(it) }
        )
    }
}

@Composable
private fun SftpFields(state: ProfileEditorUiState, viewModel: ProfileManagementViewModel) {
    OutlinedTextField(
        value = state.sftpHost,
        onValueChange = { viewModel.updateSftpHost(it) },
        label = { Text("Host") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpPort,
        onValueChange = { viewModel.updateSftpPort(it) },
        label = { Text("Port") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpUsername,
        onValueChange = { viewModel.updateSftpUsername(it) },
        label = { Text("Username") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpPassword,
        onValueChange = { viewModel.updateSftpPassword(it) },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpKeyPath,
        onValueChange = { viewModel.updateSftpKeyPath(it) },
        label = { Text("Key Path (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpKeyPassphrase,
        onValueChange = { viewModel.updateSftpKeyPassphrase(it) },
        label = { Text("Key Passphrase (optional)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpRemotePath,
        onValueChange = { viewModel.updateSftpRemotePath(it) },
        label = { Text("Remote Path") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpHttpUrl,
        onValueChange = { viewModel.updateSftpHttpUrl(it) },
        label = { Text("HTTP URL Prefix") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CustomHttpFields(state: ProfileEditorUiState, viewModel: ProfileManagementViewModel) {
    OutlinedTextField(
        value = state.customHttpUrl,
        onValueChange = { viewModel.updateCustomHttpUrl(it) },
        label = { Text("URL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.customHttpMethod,
        onValueChange = { viewModel.updateCustomHttpMethod(it) },
        label = { Text("Method") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.customHttpHeaders,
        onValueChange = { viewModel.updateCustomHttpHeaders(it) },
        label = { Text("Headers (key=value, one per line)") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.customHttpJsonPath,
        onValueChange = { viewModel.updateCustomHttpJsonPath(it) },
        label = { Text("Response URL JSON Path") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.customHttpFormField,
        onValueChange = { viewModel.updateCustomHttpFormField(it) },
        label = { Text("Form Field Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

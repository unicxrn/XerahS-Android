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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.LumenSwitch
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.PillCta
import com.xerahs.android.core.ui.lumen.hostColor
import com.xerahs.android.feature.settings.AfterUploadActionChips
import com.xerahs.android.feature.settings.destinations.NativeDestinationFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    onBack: () -> Unit,
    viewModel: ProfileManagementViewModel
) {
    val state by viewModel.editorState.collectAsState()

    Scaffold(
        topBar = {
            LumenTopBar(
                title = if (state.isEditing) "Edit Profile" else "New Profile",
                onBack = onBack
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
                shape = RoundedCornerShape(18.dp),
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
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(state.destination.hostColor())
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
                                            .background(dest.hostColor())
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
                LumenSwitch(
                    checked = state.isDefault,
                    onCheckedChange = { viewModel.updateEditorDefault(it) }
                )
            }

            HorizontalDivider(color = Lumen.tokens.hairline)
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
                UploadDestination.NEXTCLOUD, UploadDestination.IMMICH, UploadDestination.GITHUB_GIST -> {
                    NativeDestinationFields(state.destination, state.nativeValues, viewModel::updateNativeValue)
                    state.nativeError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use default after-upload actions", Modifier.weight(1f))
                LumenSwitch(checked = state.afterUploadActions == null, onCheckedChange = viewModel::setUseDefaultActions)
            }
            state.afterUploadActions?.let { AfterUploadActionChips(it, viewModel::toggleAfterUploadAction) }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Save button (primary, full-width) ---
            PillCta(
                text = "Save Profile",
                onClick = { viewModel.saveProfile(onComplete = onBack) },
                modifier = Modifier.fillMaxWidth()
            )

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
        LumenSwitch(
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
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.imgurClientSecret,
        onValueChange = { viewModel.updateImgurClientSecret(it) },
        label = { Text("Client Secret") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
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
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3SecretAccessKey,
        onValueChange = { viewModel.updateS3SecretAccessKey(it) },
        label = { Text("Secret Access Key") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3Region,
        onValueChange = { viewModel.updateS3Region(it) },
        label = { Text("Region") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3Bucket,
        onValueChange = { viewModel.updateS3Bucket(it) },
        label = { Text("Bucket") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3Endpoint,
        onValueChange = { viewModel.updateS3Endpoint(it) },
        label = { Text("Custom Endpoint (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3CustomUrl,
        onValueChange = { viewModel.updateS3CustomUrl(it) },
        label = { Text("Custom URL (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3Prefix,
        onValueChange = { viewModel.updateS3Prefix(it) },
        label = { Text("Key Prefix") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.s3Acl,
        onValueChange = { viewModel.updateS3Acl(it) },
        label = { Text("ACL (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
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
        LumenSwitch(
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
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ftpPort,
        onValueChange = { viewModel.updateFtpPort(it) },
        label = { Text("Port") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ftpUsername,
        onValueChange = { viewModel.updateFtpUsername(it) },
        label = { Text("Username") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ftpPassword,
        onValueChange = { viewModel.updateFtpPassword(it) },
        label = { Text("Password") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ftpRemotePath,
        onValueChange = { viewModel.updateFtpRemotePath(it) },
        label = { Text("Remote Path") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ftpHttpUrl,
        onValueChange = { viewModel.updateFtpHttpUrl(it) },
        label = { Text("HTTP URL Prefix") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
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
        LumenSwitch(
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
        LumenSwitch(
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
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpPort,
        onValueChange = { viewModel.updateSftpPort(it) },
        label = { Text("Port") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpUsername,
        onValueChange = { viewModel.updateSftpUsername(it) },
        label = { Text("Username") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpPassword,
        onValueChange = { viewModel.updateSftpPassword(it) },
        label = { Text("Password") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpKeyPath,
        onValueChange = { viewModel.updateSftpKeyPath(it) },
        label = { Text("Key Path (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpKeyPassphrase,
        onValueChange = { viewModel.updateSftpKeyPassphrase(it) },
        label = { Text("Key Passphrase (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpRemotePath,
        onValueChange = { viewModel.updateSftpRemotePath(it) },
        label = { Text("Remote Path") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sftpHttpUrl,
        onValueChange = { viewModel.updateSftpHttpUrl(it) },
        label = { Text("HTTP URL Prefix") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CustomHttpFields(state: ProfileEditorUiState, viewModel: ProfileManagementViewModel) {
    OutlinedTextField(
        value = state.customUploaderSxcu,
        onValueChange = { viewModel.updateCustomUploaderSxcu(it) },
        label = { Text("Custom uploader (.sxcu JSON)") },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        shape = RoundedCornerShape(18.dp),
        isError = state.customUploaderError != null,
        supportingText = {
            Text(state.customUploaderError ?: "Paste or edit a ShareX custom uploader definition")
        },
        minLines = 10,
        modifier = Modifier.fillMaxWidth()
    )
}

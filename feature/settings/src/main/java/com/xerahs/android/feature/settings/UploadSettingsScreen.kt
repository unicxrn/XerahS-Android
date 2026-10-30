package com.xerahs.android.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xerahs.android.core.common.permission.ScreenshotAccess
import com.xerahs.android.core.common.permission.screenshotAccess
import com.xerahs.android.core.common.permission.screenshotPermissions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.domain.LinkFormatter
import com.xerahs.android.core.domain.model.FileKind
import com.xerahs.android.core.domain.model.LinkFormat
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.ui.lumen.monoStyle
import androidx.compose.ui.text.style.TextOverflow
import com.xerahs.android.core.domain.model.ImageFormat
import com.xerahs.android.core.domain.model.UploadDestination
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard
import com.xerahs.android.core.ui.lumen.IconTile
import com.xerahs.android.core.ui.lumen.Lumen
import com.xerahs.android.core.ui.lumen.LumenSwitch
import com.xerahs.android.core.ui.lumen.LumenTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UploadSettingsScreen(
    onNavigateToImgurConfig: () -> Unit,
    onNavigateToS3Config: () -> Unit,
    onNavigateToFtpConfig: () -> Unit,
    onNavigateToCustomHttpConfig: () -> Unit,
    onNavigateToImportUploader: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToDestination: (UploadDestination) -> Unit = {},
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { LumenTopBar(title = "Uploads", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Destinations section - pick default + configure each service
            SectionHeader("Destinations")

            SettingsGroupCard {
                var dropdownExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.defaultDestination.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Default Upload Destination") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        UploadDestination.entries.forEach { dest ->
                            DropdownMenuItem(
                                text = { Text(dest.displayName) },
                                onClick = {
                                    viewModel.setDefaultDestination(dest)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Destination health check
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.destinationConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (uiState.destinationConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(start = 0.dp)
                    )
                    Text(
                        text = if (uiState.destinationConfigured) "Configured" else "Not configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.destinationConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Lumen.tokens.hairline
                )

                var destinationsExpanded by rememberSaveable { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Configure Destinations") },
                    supportingContent = { Text("Imgur, S3, FTP, Nextcloud, Immich, Gist, Custom uploader") },
                    trailingContent = {
                        Icon(
                            if (destinationsExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (destinationsExpanded) "Collapse" else "Expand"
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { destinationsExpanded = !destinationsExpanded }
                )

                AnimatedVisibility(
                    visible = destinationsExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Lumen.tokens.hairline
                        )

                        DestinationItem(
                            icon = Icons.Default.Image,
                            title = "Imgur",
                            subtitle = "Configure Imgur upload",
                            onClick = onNavigateToImgurConfig
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Lumen.tokens.hairline
                        )

                        DestinationItem(
                            icon = Icons.Default.Cloud,
                            title = "Amazon S3",
                            subtitle = "Configure S3 bucket upload",
                            onClick = onNavigateToS3Config
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Lumen.tokens.hairline
                        )

                        DestinationItem(
                            icon = Icons.Default.Dns,
                            title = "FTP / SFTP",
                            subtitle = "Configure FTP/SFTP upload",
                            onClick = onNavigateToFtpConfig
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Lumen.tokens.hairline
                        )

                        DestinationItem(icon = Icons.Default.FolderShared, title = "Nextcloud", subtitle = "Upload to your Nextcloud", onClick = { onNavigateToDestination(UploadDestination.NEXTCLOUD) })

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Lumen.tokens.hairline
                        )

                        DestinationItem(icon = Icons.Default.PhotoLibrary, title = "Immich", subtitle = "Add to your photo library", onClick = { onNavigateToDestination(UploadDestination.IMMICH) })

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Lumen.tokens.hairline
                        )

                        DestinationItem(icon = Icons.Default.Code, title = "GitHub Gist", subtitle = "Text and code snippets", onClick = { onNavigateToDestination(UploadDestination.GITHUB_GIST) })

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Lumen.tokens.hairline
                        )

                        DestinationItem(
                            icon = Icons.Default.Http,
                            title = "Custom uploader",
                            subtitle = "ShareX-compatible (.sxcu) uploader",
                            onClick = onNavigateToCustomHttpConfig
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Lumen.tokens.hairline
                        )

                        DestinationItem(
                            icon = Icons.Default.FileDownload,
                            title = "Import uploader",
                            subtitle = "ShareX .sxcu or XerahS .xsdc file",
                            onClick = onNavigateToImportUploader
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Lumen.tokens.hairline
                )

                ListItem(
                    headlineContent = { Text("Upload Profiles") },
                    supportingContent = { Text("Save named configurations for each destination") },
                    leadingContent = { IconTile(Icons.Default.AccountTree, size = 38.dp) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onNavigateToProfiles)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Image Processing section - quality and resize settings
            SectionHeader("Image Processing")

            SettingsGroupCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quality: ${uiState.imageQuality}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Slider(
                        value = uiState.imageQuality.toFloat(),
                        onValueChange = { viewModel.setImageQuality(it.toInt()) },
                        valueRange = 10f..100f,
                        steps = 17,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Lower quality = smaller file size. Only applies to JPEG compression.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Max Image Dimension",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val dimensions = listOf(
                            0 to "Original",
                            1920 to "1920px",
                            1440 to "1440px",
                            1080 to "1080px",
                            720 to "720px"
                        )
                        dimensions.forEach { (dim, label) ->
                            FilterChip(
                                selected = uiState.maxImageDimension == dim,
                                onClick = { viewModel.setMaxImageDimension(dim) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        text = "Resize images before uploading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Output Format",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ImageFormat.entries.forEach { format ->
                            FilterChip(
                                selected = uiState.uploadFormat == format,
                                onClick = { viewModel.setUploadFormat(format) },
                                label = { Text(format.displayName) }
                            )
                        }
                    }
                    Text(
                        text = "Convert images before uploading. Original keeps the source format.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Lumen.tokens.hairline
                )

                ListItem(
                    headlineContent = { Text("Strip EXIF metadata") },
                    supportingContent = { Text("Remove GPS, camera info, and other metadata before uploading") },
                    trailingContent = {
                        LumenSwitch(
                            checked = uiState.stripExif,
                            onCheckedChange = { viewModel.setStripExif(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Lumen.tokens.hairline
                )

                ListItem(
                    headlineContent = { Text("Convert HEIC to PNG") },
                    supportingContent = { Text("Most hosts can't display HEIC photos (Android 9+)") },
                    trailingContent = {
                        LumenSwitch(
                            checked = uiState.convertHeicToPng,
                            onCheckedChange = { viewModel.setConvertHeicToPng(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                if (uiState.shortenerProfiles.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = Lumen.tokens.hairline
                    )
                    var shortenerMenu by remember { mutableStateOf(false) }
                    val current = uiState.shortenerProfiles.find { it.id == uiState.shortenerProfileId }
                    ListItem(
                        headlineContent = { Text("URL shortener") },
                        supportingContent = { Text(current?.name ?: "is.gd (built-in)") },
                        modifier = Modifier.clickable { shortenerMenu = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        trailingContent = {
                            DropdownMenu(expanded = shortenerMenu, onDismissRequest = { shortenerMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("is.gd (built-in)") },
                                    onClick = { viewModel.setShortenerProfileId(null); shortenerMenu = false }
                                )
                                uiState.shortenerProfiles.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name) },
                                        onClick = { viewModel.setShortenerProfileId(p.id); shortenerMenu = false }
                                    )
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Behavior section - naming, clipboard, and other general settings
            SectionHeader("Behavior")

            SettingsGroupCard {
                OutlinedTextField(
                    value = uiState.fileNamingPattern,
                    onValueChange = { viewModel.setFileNamingPattern(it) },
                    label = { Text("File Naming Pattern") },
                    supportingText = {
                        Text("Tokens: {original}, {date}, {time}, {timestamp}, {random}")
                    },
                    leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Lumen.tokens.hairline
                )

                Column(Modifier.padding(16.dp)) {
                    Text("After upload", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Runs after each upload unless the profile has its own actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AfterUploadActionChips(
                        selected = uiState.defaultAfterUploadActions,
                        onToggle = viewModel::toggleDefaultAfterUploadAction,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Lumen.tokens.hairline
                )

                LinkFormatRow(current = uiState.linkFormat, onSelect = viewModel::setLinkFormat)
            }

            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader("Routing")

            SettingsGroupCard {
                listOf(
                    FileKind.IMAGE to "Images go to",
                    FileKind.TEXT to "Text goes to",
                    FileKind.OTHER to "Other files go to",
                ).forEachIndexed { index, (kind, title) ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Lumen.tokens.hairline
                        )
                    }
                    RouteRow(
                        title = title,
                        defaultLabel = "Default (${uiState.defaultDestination.displayName})",
                        profiles = uiState.allProfiles,
                        selectedId = uiState.routes[kind],
                        onSelect = { viewModel.setRouteProfileId(kind, it) },
                        onAddProfile = onNavigateToProfiles
                    )
                }
                Text(
                    "Picked for you on the upload screen. You can still change it there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ScreenshotSettings(
                watch = uiState.screenshotWatch,
                auto = uiState.screenshotAutoUpload,
                permissionLost = uiState.screenshotPermissionLost,
                onWatchChange = viewModel::setScreenshotWatch,
                onAutoChange = viewModel::setScreenshotAutoUpload,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScreenshotSettings(
    watch: Boolean,
    auto: Boolean,
    permissionLost: Boolean,
    onWatchChange: (Boolean) -> Unit,
    onAutoChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(context.screenshotAccess()) }
    var denied by remember { mutableStateOf(false) }
    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // Access can change in system settings while we are in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = context.screenshotAccess()
                notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = context.screenshotAccess()
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (access == ScreenshotAccess.DENIED) {
            denied = true
            onWatchChange(false)
        } else {
            denied = false
            onWatchChange(true)
        }
    }

    SectionHeader("Screenshots")
    SettingsGroupCard {
        ListItem(
            headlineContent = { Text("Offer to upload new screenshots") },
            supportingContent = { Text("Get a notification with Upload and Edit when you take one") },
            trailingContent = {
                LumenSwitch(
                    checked = watch,
                    onCheckedChange = { on ->
                        if (!on) {
                            denied = false
                            onWatchChange(false)
                        } else if (
                            context.screenshotAccess() != ScreenshotAccess.DENIED &&
                            (auto || Build.VERSION.SDK_INT < 33 || notificationsOn)
                        ) {
                            access = context.screenshotAccess()
                            denied = false
                            onWatchChange(true)
                        } else {
                            launcher.launch(screenshotPermissions(notifications = !auto))
                        }
                    }
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        if ((denied || permissionLost) && !watch) {
            Text(
                "XerahS needs photo access to see new screenshots",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (watch && access == ScreenshotAccess.LIMITED) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "XerahS can only see photos you picked. Allow all photos to catch new screenshots",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { openAppSettings() }, Modifier.offset(x = (-12).dp)) { Text("Open app settings") }
            }
        }

        if (watch && !auto && !notificationsOn) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Notifications are off, so XerahS can't offer uploads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { openAppSettings() }, Modifier.offset(x = (-12).dp)) { Text("Open app settings") }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = Lumen.tokens.hairline
        )

        ListItem(
            headlineContent = { Text("Upload new screenshots without asking") },
            supportingContent = { Text("Sends them to your default destination") },
            trailingContent = {
                LumenSwitch(checked = auto, onCheckedChange = onAutoChange, enabled = watch)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun DestinationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { IconTile(icon, size = 38.dp) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * Profile picker for one file kind. "Default" names the destination it resolves to, and a
 * missing (deleted) profile shows as Default. With no profiles, the menu says how to get some.
 */
@Composable
private fun RouteRow(
    title: String,
    defaultLabel: String,
    profiles: List<UploadProfile>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onAddProfile: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val current = profiles.find { it.id == selectedId }
    val check: @Composable () -> Unit = { Icon(Icons.Default.Check, contentDescription = "Selected") }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(current?.let { "${it.name} (${it.destination.displayName})" } ?: defaultLabel)
        },
        modifier = Modifier.clickable { menu = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        trailingContent = {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(defaultLabel) },
                    trailingIcon = if (current == null) check else null,
                    onClick = { onSelect(null); menu = false }
                )
                profiles.forEach { p ->
                    DropdownMenuItem(
                        text = { Text("${p.name} (${p.destination.displayName})") },
                        trailingIcon = if (current?.id == p.id) check else null,
                        onClick = { onSelect(p.id); menu = false }
                    )
                }
                if (profiles.isEmpty()) {
                    HorizontalDivider(color = Lumen.tokens.hairline)
                    DropdownMenuItem(
                        text = { Text("Add a profile to send these somewhere else") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { menu = false; onAddProfile() }
                    )
                }
            }
        }
    )
}

@Composable
private fun LinkFormatRow(current: LinkFormat, onSelect: (LinkFormat) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("Copy link as") },
        supportingContent = {
            Column {
                Text(current.label)
                Text(
                    LinkFormatter.format("https://example.com/photo.png", "photo.png", true, current),
                    style = monoStyle(12),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        modifier = Modifier.clickable { menu = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        trailingContent = {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                LinkFormat.entries.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format.label) },
                        onClick = { onSelect(format); menu = false }
                    )
                }
            }
        }
    )
}

package com.xerahs.android.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToUploads: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsCategoryCard(
                icon = Icons.Default.Palette,
                title = "Appearance",
                subtitle = "Theme, display options",
                onClick = onNavigateToAppearance,
                index = 0
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsCategoryCard(
                icon = Icons.Default.CloudUpload,
                title = "Uploads",
                subtitle = "Destinations, file naming",
                onClick = onNavigateToUploads,
                index = 1
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsCategoryCard(
                icon = Icons.Default.Backup,
                title = "Backup",
                subtitle = "Export and import settings",
                onClick = onNavigateToBackup,
                index = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsCategoryCard(
                icon = Icons.Default.Lock,
                title = "Security",
                subtitle = "Biometric lock settings",
                onClick = onNavigateToSecurity,
                index = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsCategoryCard(
                icon = Icons.Default.SystemUpdate,
                title = "Updates",
                subtitle = "Check for updates & changelog",
                onClick = onNavigateToUpdates,
                index = 4
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "XerahS v$versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    index: Int
) {
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * 100
            )
        ) + slideInHorizontally(
            initialOffsetX = { it / 3 },
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * 100
            )
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = title, onClick = onClick),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

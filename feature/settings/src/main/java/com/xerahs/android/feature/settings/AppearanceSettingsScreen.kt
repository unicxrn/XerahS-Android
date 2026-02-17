package com.xerahs.android.feature.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.domain.model.ColorTheme
import com.xerahs.android.core.domain.model.ThemeMode
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard

private fun ColorTheme.previewColor(): Color = when (this) {
    ColorTheme.VIOLET -> Color(0xFF6B35E8)
    ColorTheme.TEAL -> Color(0xFF006A6A)
    ColorTheme.BLUE -> Color(0xFF0061A4)
    ColorTheme.GREEN -> Color(0xFF386A20)
    ColorTheme.ORANGE -> Color(0xFF8B5000)
    ColorTheme.PINK -> Color(0xFF984061)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateToThemeEditor: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
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
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Display")

            SettingsGroupCard {
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = {
                                        Text(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> "System"
                                                ThemeMode.LIGHT -> "Light"
                                                ThemeMode.DARK -> "Dark"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = "Theme mode") }
                )
            }

            AnimatedVisibility(
                visible = uiState.themeMode == ThemeMode.DARK || uiState.themeMode == ThemeMode.SYSTEM
            ) {
                Column {
                    SettingsGroupCard {
                        ListItem(
                            headlineContent = { Text("OLED Black") },
                            supportingContent = { Text("Pure black backgrounds for AMOLED displays") },
                            trailingContent = {
                                Switch(
                                    checked = uiState.oledBlack,
                                    onCheckedChange = { viewModel.setOledBlack(it) }
                                )
                            }
                        )
                    }
                }
            }

            SectionHeader("Material You")

            SettingsGroupCard {
                ListItem(
                    headlineContent = { Text("Dynamic Color") },
                    supportingContent = {
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                "Use wallpaper-based colors (Android 12+)"
                            else
                                "Requires Android 12 or higher"
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = uiState.dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        )
                    }
                )
            }

            AnimatedVisibility(visible = !uiState.dynamicColor) {
                Column {
                    SectionHeader("Color Theme")

                    SettingsGroupCard {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ColorTheme.entries.forEach { theme ->
                                val isSelected = uiState.colorTheme == theme && uiState.customThemeId == null
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable(
                                        role = Role.RadioButton,
                                        onClickLabel = theme.displayName
                                    ) {
                                        viewModel.clearCustomTheme()
                                        viewModel.setColorTheme(theme)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    shape = CircleShape
                                                ) else Modifier
                                            )
                                            .padding(2.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = theme.previewColor()
                                        ) {}
                                    }
                                    Text(
                                        text = theme.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.customThemes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SectionHeader("Custom Themes")
                        SettingsGroupCard {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                uiState.customThemes.forEach { custom ->
                                    val isSelected = uiState.customThemeId == custom.id
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable(
                                            role = Role.RadioButton,
                                            onClickLabel = custom.name
                                        ) {
                                            viewModel.selectCustomTheme(custom.id)
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier.size(40.dp),
                                            contentAlignment = Alignment.TopEnd
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .then(
                                                        if (isSelected) Modifier.border(
                                                            width = 2.dp,
                                                            color = MaterialTheme.colorScheme.outline,
                                                            shape = CircleShape
                                                        ) else Modifier
                                                    )
                                                    .padding(2.dp)
                                            ) {
                                                Surface(
                                                    modifier = Modifier.size(36.dp),
                                                    shape = CircleShape,
                                                    color = Color(custom.seedColor)
                                                ) {}
                                            }
                                            Surface(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { viewModel.deleteCustomTheme(custom.id) },
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.errorContainer
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Delete",
                                                        modifier = Modifier.size(10.dp),
                                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = custom.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(top = 4.dp),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsGroupCard {
                        ListItem(
                            headlineContent = { Text("Create Custom Theme") },
                            supportingContent = { Text("Generate a theme from any color") },
                            leadingContent = {
                                FilledTonalIconButton(onClick = onNavigateToThemeEditor) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                            },
                            modifier = Modifier.clickable(onClick = onNavigateToThemeEditor)
                        )
                    }
                }
            }
        }
    }
}

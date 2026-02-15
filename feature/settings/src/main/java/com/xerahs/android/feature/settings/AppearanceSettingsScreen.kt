package com.xerahs.android.feature.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
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
                                val isSelected = uiState.colorTheme == theme
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable(
                                        role = Role.RadioButton,
                                        onClickLabel = theme.displayName
                                    ) {
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
                }
            }
        }
    }
}

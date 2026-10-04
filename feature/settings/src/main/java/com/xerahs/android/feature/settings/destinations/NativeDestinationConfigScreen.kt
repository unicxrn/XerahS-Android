package com.xerahs.android.feature.settings.destinations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.SettingsGroupCard
import com.xerahs.android.core.ui.lumen.LumenTopBar
import com.xerahs.android.core.ui.lumen.PillCta
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeDestinationConfigScreen(
    onBack: () -> Unit,
    viewModel: NativeDestinationConfigViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loaded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!loaded) { values = viewModel.load(); loaded = true }
    }

    Scaffold(
        topBar = { LumenTopBar(title = viewModel.destination.displayName, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroupCard(Modifier.padding(top = 8.dp)) {
                NativeDestinationFields(
                    destination = viewModel.destination,
                    values = values,
                    onChange = { key, value -> values = values + (key to value) },
                    modifier = Modifier.padding(16.dp)
                )
            }
            PillCta(
                text = "Save",
                onClick = {
                    scope.launch { snackbar.showSnackbar(viewModel.save(values) ?: "Saved") }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

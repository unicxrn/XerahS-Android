package com.xerahs.android.feature.settings.destinations

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.core.ui.SectionHeader
import com.xerahs.android.core.ui.SettingsGroupCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHttpConfigScreen(
    onBack: () -> Unit,
    onImportAsProfile: () -> Unit = {},
    viewModel: CustomHttpConfigViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var sxcu by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!loaded) {
            sxcu = viewModel.loadSxcu()
            loaded = true
        }
    }

    fun replaceWith(text: String?) {
        val normalized = text?.let(viewModel::normalize)
        if (normalized == null) {
            scope.launch { snackbarHostState.showSnackbar("Not a valid .sxcu custom uploader") }
        } else {
            sxcu = normalized
            error = null
            scope.launch { snackbarHostState.showSnackbar("Loaded — review and tap Save") }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            replaceWith(runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom uploader") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Load")
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { fileLauncher.launch(arrayOf("*/*")) }) { Text("Open .sxcu") }
                OutlinedButton(onClick = { replaceWith(clipboard.getText()?.text) }) { Text("Paste") }
            }
            TextButton(onClick = onImportAsProfile, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Import as a new profile instead…")
            }

            SectionHeader("Definition")
            SettingsGroupCard {
                OutlinedTextField(
                    value = sxcu,
                    onValueChange = { sxcu = it; error = null },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    isError = error != null,
                    supportingText = { Text(error ?: "ShareX .sxcu JSON — supports {json:}, {regex:}, {xml:}, {inputbox:} …") },
                    minLines = 14,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = viewModel.save(sxcu)
                            busy = false
                            if (error == null) snackbarHostState.showSnackbar("Saved")
                        }
                    }
                ) { Text("Save") }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val result = viewModel.testConnection(sxcu)
                            busy = false
                            snackbarHostState.showSnackbar(result)
                        }
                    }
                ) { Text("Test endpoint") }
            }
        }
    }
}

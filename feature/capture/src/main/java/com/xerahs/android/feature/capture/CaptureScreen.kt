package com.xerahs.android.feature.capture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.ui.lumen.PillCta
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CaptureScreen(
    onImageCaptured: (String) -> Unit,
    onMultiImageCaptured: (List<String>) -> Unit = {},
    onFileCaptured: (String) -> Unit = {},
    onCancel: () -> Unit = {},
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onPicked(uris)
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onPicked(uris)
    }
    val pickMedia = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    val launched = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!launched.value) {
            launched.value = true
            pickMedia()
        }
    }

    // A partial failure still routes the files that did copy; give the snackbar a moment first.
    LaunchedEffect(uiState.pickedFilePath, uiState.pickedImagePath, uiState.pickedImagePaths) {
        val file = uiState.pickedFilePath
        val image = uiState.pickedImagePath
        val images = uiState.pickedImagePaths
        if (file == null && image == null && images == null) return@LaunchedEffect
        if (uiState.errorMessage != null) delay(1500)
        viewModel.onImageHandled()
        when {
            file != null -> onFileCaptured(file)
            image != null -> onImageCaptured(image)
            images != null -> onMultiImageCaptured(images)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (uiState.isCopying) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                }
                PillCta(text = "Pick photos or videos", onClick = { pickMedia() }, enabled = !uiState.isCopying)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    shape = CircleShape,
                    enabled = !uiState.isCopying
                ) { Text("Pick a file") }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancel, enabled = !uiState.isCopying) { Text("Cancel") }
            }
        }
    }
}

package com.xerahs.android.feature.upload.intake

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xerahs.android.core.ui.lumen.Eyebrow
import com.xerahs.android.core.ui.lumen.PillCta
import com.xerahs.android.core.ui.lumen.monoStyle

/**
 * The sheet for a shared or pasted link: shorten it, upload the file it points to, or upload it
 * as text. In "Upload from link" mode it asks for the link first. Pass the activity-scoped
 * [viewModel] so the sheet survives rotation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkIntakeSheet(viewModel: LinkIntakeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LinkSheetEvent.Shortened -> {
                    clipboard.setText(AnnotatedString(event.shortUrl))
                    Toast.makeText(context, "Short link copied", Toast.LENGTH_SHORT).show()
                }
                is LinkSheetEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (!state.visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = viewModel::dismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        // The default insets pad the whole sheet window, leaving the screen below showing through
        // under the nav bar. Draw to the edges; the content pads itself for the nav bar.
        windowInsets = WindowInsets(0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                state.downloading -> DownloadingContent(state, onCancel = viewModel::cancelDownload)
                state.url == null -> EntryContent(state, viewModel)
                else -> OptionsContent(state, state.url!!, viewModel)
            }
        }
    }
}

@Composable
private fun EntryContent(state: LinkSheetState, viewModel: LinkIntakeViewModel) {
    Eyebrow("Upload from link")
    Text(
        "Paste a link to a file. It is downloaded, then uploaded like any other file.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = state.draft,
        onValueChange = viewModel::onDraftChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("https://") },
        singleLine = true,
        isError = state.error != null,
        supportingText = state.error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.uploadFile() }),
        shape = RoundedCornerShape(18.dp)
    )
    PillCta(
        text = "Download and upload",
        onClick = viewModel::uploadFile,
        icon = Icons.Outlined.CloudDownload,
        enabled = state.draft.isNotBlank()
    )
}

@Composable
private fun OptionsContent(state: LinkSheetState, url: String, viewModel: LinkIntakeViewModel) {
    Eyebrow("Link")
    Text(url, style = monoStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
    state.error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.size(4.dp))
    PillCta(
        text = "Upload the file it points to",
        onClick = viewModel::uploadFile,
        icon = Icons.Outlined.CloudDownload
    )
    OutlinedButton(
        onClick = viewModel::shorten,
        enabled = !state.shortening,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (state.shortening) "Shortening" else "Shorten link") }
    OutlinedButton(
        onClick = viewModel::uploadAsText,
        enabled = !state.shortening,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Upload as text") }
}

@Composable
private fun DownloadingContent(state: LinkSheetState, onCancel: () -> Unit) {
    Eyebrow("Downloading")
    Text(
        state.url ?: state.draft,
        style = monoStyle(),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    if (state.progress < 0f) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        Text(
            "${(state.progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    OutlinedButton(
        onClick = onCancel,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Cancel") }
}

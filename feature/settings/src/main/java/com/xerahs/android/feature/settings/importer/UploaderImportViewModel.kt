package com.xerahs.android.feature.settings.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.common.sxcu.SxcuPresets
import com.xerahs.android.core.domain.model.INSECURE_HTTP_WARNING
import com.xerahs.android.core.domain.model.ImportDraft
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploaderFileKind
import com.xerahs.android.core.domain.repository.UploaderImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SelectableDraft(val draft: ImportDraft, val selected: Boolean = true)

sealed interface UploaderImportState {
    data object Idle : UploaderImportState
    data object Loading : UploaderImportState
    data class NeedsPassphrase(val error: String? = null) : UploaderImportState
    data class Preview(val items: List<SelectableDraft>, val skipped: List<String> = emptyList()) : UploaderImportState
    data class Done(val count: Int) : UploaderImportState
    data class Error(val message: String) : UploaderImportState
}

@HiltViewModel
class UploaderImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: UploaderImportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UploaderImportState>(UploaderImportState.Idle)
    val state: StateFlow<UploaderImportState> = _state.asStateFlow()

    private var pendingXsdc: ByteArray? = null

    init {
        savedStateHandle.get<String>("uri")?.takeIf { it.isNotBlank() }?.let { loadUri(Uri.parse(it)) }
    }

    fun loadUri(uri: Uri) {
        viewModelScope.launch {
            _state.value = UploaderImportState.Loading
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    bytes to name
                }
            }
            read.fold(
                onSuccess = { (bytes, name) -> handle(bytes, name) },
                onFailure = { _state.value = UploaderImportState.Error("Couldn't read the file.") }
            )
        }
    }

    fun loadText(text: String?) {
        if (text.isNullOrBlank()) {
            _state.value = UploaderImportState.Error("The clipboard is empty.")
            return
        }
        handle(text.toByteArray(), null)
    }

    private fun handle(bytes: ByteArray, name: String?) {
        if (bytes.size > MAX_BYTES) {
            _state.value = UploaderImportState.Error("That file is too large to be an uploader config.")
            return
        }
        _state.value = when (repository.detect(bytes, name)) {
            UploaderFileKind.SXCU -> repository.parseSxcu(bytes).fold(
                onSuccess = { UploaderImportState.Preview(listOf(SelectableDraft(it))) },
                onFailure = { UploaderImportState.Error(it.message ?: "Not a valid .sxcu file.") }
            )
            UploaderFileKind.XSDC -> {
                pendingXsdc = bytes
                UploaderImportState.NeedsPassphrase()
            }
            UploaderFileKind.UNKNOWN -> UploaderImportState.Error("This isn't a .sxcu or .xsdc file.")
        }
    }

    fun submitPassphrase(passphrase: CharArray) {
        val bytes = pendingXsdc ?: return
        viewModelScope.launch {
            _state.value = UploaderImportState.Loading
            val result = withContext(Dispatchers.Default) { repository.parseXsdc(bytes, passphrase) }
            passphrase.fill('\u0000')
            _state.value = result.fold(
                onSuccess = {
                    if (it.drafts.isEmpty()) UploaderImportState.Error(
                        "No destinations in this file can be used on Android." +
                            if (it.skipped.isNotEmpty()) " Skipped: ${it.skipped.joinToString()}" else ""
                    ) else UploaderImportState.Preview(it.drafts.map { d -> SelectableDraft(d) }, it.skipped)
                },
                onFailure = { UploaderImportState.NeedsPassphrase(it.message) }
            )
        }
    }

    fun loadPreset(id: String, values: Map<String, String>) {
        _state.value = SxcuPresets.render(id, values).fold(
            onSuccess = { spec ->
                val warnings = if (spec.requestURL.trim().startsWith("http://", ignoreCase = true)) listOf(INSECURE_HTTP_WARNING) else emptyList()
                UploaderImportState.Preview(
                    listOf(SelectableDraft(ImportDraft(spec.name, UploadDestination.CUSTOM_HTTP, UploadConfig.CustomUploaderConfig(spec), warnings)))
                )
            },
            onFailure = { UploaderImportState.Error("Couldn't build the preset: ${it.message}") }
        )
    }

    fun toggleSelected(index: Int) = updateItem(index) { it.copy(selected = !it.selected) }

    fun toggleDefault(index: Int) = updateItem(index) { it.copy(draft = it.draft.copy(makeDefault = !it.draft.makeDefault)) }

    private fun updateItem(index: Int, change: (SelectableDraft) -> SelectableDraft) {
        val preview = _state.value as? UploaderImportState.Preview ?: return
        _state.value = preview.copy(items = preview.items.mapIndexed { i, item -> if (i == index) change(item) else item })
    }

    /** True when any selected draft sends data over plain HTTP — the UI must confirm first. */
    fun needsInsecureConfirmation(): Boolean =
        (_state.value as? UploaderImportState.Preview)?.items
            ?.any { it.selected && INSECURE_HTTP_WARNING in it.draft.warnings } == true

    fun import() {
        val preview = _state.value as? UploaderImportState.Preview ?: return
        val drafts = preview.items.filter { it.selected }.map { it.draft }
        if (drafts.isEmpty()) return
        viewModelScope.launch {
            _state.value = UploaderImportState.Loading
            _state.value = try {
                UploaderImportState.Done(repository.import(drafts))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                UploaderImportState.Error("Couldn't save the imported profiles: ${e.message}")
            }
        }
    }

    fun reset() {
        pendingXsdc = null
        _state.value = UploaderImportState.Idle
    }

    private companion object {
        const val MAX_BYTES = 1_000_000
    }
}

package com.xerahs.android.feature.upload.intake

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.core.domain.repository.UrlShortenerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** What the link sheet shows. */
data class LinkSheetState(
    /** The link the options apply to; null with [entry] false means the sheet is closed. */
    val url: String? = null,
    /** "Upload from link": the user types or pastes the link first. */
    val entry: Boolean = false,
    val draft: String = "",
    val downloading: Boolean = false,
    /** 0..1, or -1 while the size is unknown. */
    val progress: Float = 0f,
    val shortening: Boolean = false,
    val error: String? = null,
) {
    val visible: Boolean get() = url != null || entry
}

sealed interface LinkSheetEvent {
    data class Shortened(val shortUrl: String) : LinkSheetEvent
    data class Message(val text: String) : LinkSheetEvent
}

/**
 * Drives the shared-link sheet. Scoped to the activity so the sheet, its link and a running
 * download survive rotation; the link and mode are also kept in [SavedStateHandle].
 * A finished file is published in [readyPath] for the app to route like any other shared file.
 */
@HiltViewModel
class LinkIntakeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: UrlDownloader,
    private val shortener: UrlShortenerRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private data class Work(
        val downloading: Boolean = false,
        val progress: Float = 0f,
        val shortening: Boolean = false,
        val error: String? = null,
    )

    private val work = MutableStateFlow(Work())
    private var downloadJob: Job? = null

    val state: StateFlow<LinkSheetState> = combine(
        savedStateHandle.getStateFlow<String?>(KEY_URL, null),
        savedStateHandle.getStateFlow(KEY_ENTRY, false),
        savedStateHandle.getStateFlow(KEY_DRAFT, ""),
        work,
    ) { url, entry, draft, w ->
        LinkSheetState(url, entry, draft, w.downloading, w.progress, w.shortening, w.error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LinkSheetState())

    /** Path of a downloaded or written file waiting to be opened in the upload flow. */
    val readyPath: StateFlow<String?> = savedStateHandle.getStateFlow<String?>(KEY_READY, null)
    fun consumeReady() { savedStateHandle[KEY_READY] = null }

    private val _events = Channel<LinkSheetEvent>(Channel.BUFFERED)
    val events: Flow<LinkSheetEvent> = _events.receiveAsFlow()

    /** Opens the sheet with the three options for [url]. */
    fun open(url: String) {
        cancelDownload()
        work.value = Work()
        savedStateHandle[KEY_ENTRY] = false
        savedStateHandle[KEY_URL] = url
    }

    /** Opens "Upload from link", optionally prefilled. */
    fun openEntry(prefill: String = "") {
        cancelDownload()
        work.value = Work()
        savedStateHandle[KEY_URL] = null
        savedStateHandle[KEY_DRAFT] = prefill
        savedStateHandle[KEY_ENTRY] = true
    }

    fun onDraftChange(text: String) {
        savedStateHandle[KEY_DRAFT] = text
        if (work.value.error != null) work.value = work.value.copy(error = null)
    }

    fun dismiss() {
        cancelDownload()
        work.value = Work()
        savedStateHandle[KEY_URL] = null
        savedStateHandle[KEY_ENTRY] = false
        savedStateHandle[KEY_DRAFT] = ""
    }

    fun shorten() {
        val url = state.value.url ?: return
        if (work.value.shortening) return
        work.value = work.value.copy(shortening = true, error = null)
        viewModelScope.launch {
            shortener.shorten(url)
                .onSuccess {
                    work.value = Work()
                    _events.trySend(LinkSheetEvent.Shortened(it))
                    dismiss()
                }
                .onFailure { work.value = work.value.copy(shortening = false, error = "Couldn't shorten that link") }
        }
    }

    /** Downloads the link (from the options, or the typed link in entry mode). */
    fun uploadFile() {
        val s = state.value
        val url = s.url ?: singleUrlOrNull(s.draft)
        if (url == null) {
            work.value = work.value.copy(error = "Enter a link that starts with http:// or https://")
            return
        }
        if (downloadJob?.isActive == true) return
        work.value = Work(downloading = true)
        downloadJob = viewModelScope.launch {
            // The downloader creates the folder off the main thread.
            val dir = sharedDir()
            try {
                val file = downloader.download(url, dir) { p -> work.value = work.value.copy(progress = p) }
                savedStateHandle[KEY_READY] = file.absolutePath
                downloadJob = null
                dismiss()
            } catch (e: CancellationException) {
                dir.deleteRecursively()
                throw e
            } catch (e: Exception) {
                dir.deleteRecursively()
                work.value = Work(error = messageFor(e))
            }
        }
    }

    /** Cancels a running download and goes back to the options. */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        if (work.value.downloading) work.value = Work()
    }

    fun uploadAsText() {
        val url = state.value.url ?: return
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching { File(sharedDir().apply { mkdirs() }, "shared-text.txt").apply { writeText(url) }.absolutePath }.getOrNull()
            }
            if (path == null) {
                _events.trySend(LinkSheetEvent.Message("Couldn't save that text"))
            } else {
                savedStateHandle[KEY_READY] = path
                dismiss()
            }
        }
    }

    /** A fresh `captures/shared_*` folder, which the capture sweep cleans up like other shares. */
    private fun sharedDir(): File = File(context.filesDir, "captures/shared_${UUID.randomUUID()}")

    private fun messageFor(e: Exception): String = when (e) {
        is FileTooLargeException -> e.message ?: "That file is too large"
        is IllegalArgumentException -> "Only http and https links can be downloaded"
        else -> "Couldn't download that link"
    }

    private companion object {
        const val KEY_URL = "linkSheetUrl"
        const val KEY_ENTRY = "linkSheetEntry"
        const val KEY_DRAFT = "linkSheetDraft"
        const val KEY_READY = "linkSheetReadyPath"
    }
}

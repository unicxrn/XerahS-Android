package com.xerahs.android.feature.tools.combine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xerahs.android.feature.tools.ImageLoading
import com.xerahs.android.feature.tools.ToolOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface CombineEvent {
    data class Upload(val path: String) : CombineEvent
    data class Share(val file: File) : CombineEvent
    data class Message(val text: String) : CombineEvent
}

enum class CombineAction { UPLOAD, SHARE, SAVE }

/**
 * Picked images are copied into this ViewModel's own cacheDir/tools_combine/<id> folder on pick
 * (the picker's grant can lapse). The folder name, the copies' paths and the options live in
 * [SavedStateHandle] so they survive rotation and process death.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CombineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val paths: StateFlow<List<String>> = savedState.getStateFlow(KEY_PATHS, arrayListOf<String>())
    val options: StateFlow<CombineOptions> = combine(
        savedState.getStateFlow(KEY_VERTICAL, true),
        savedState.getStateFlow(KEY_SPACING, 0),
        savedState.getStateFlow(KEY_BG, 0xFF000000.toInt()),
        savedState.getStateFlow(KEY_ALIGN, Align.CENTER.name),
        savedState.getStateFlow(KEY_MATCH, true),
    ) { vertical, spacing, bg, align, match ->
        CombineOptions(vertical, spacing, bg, Align.valueOf(align), match)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CombineOptions())

    private val _thumbs = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbs: StateFlow<Map<String, Bitmap>> = _thumbs.asStateFlow()

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    private val _rendering = MutableStateFlow(false)
    val rendering: StateFlow<Boolean> = _rendering.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _busy = MutableStateFlow<CombineAction?>(null)
    val busy: StateFlow<CombineAction?> = _busy.asStateFlow()

    private val _events = Channel<CombineEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val inputDir: File = File(
        File(context.cacheDir, INPUT_ROOT),
        savedState.get<String>(KEY_DIR) ?: UUID.randomUUID().toString().also { savedState[KEY_DIR] = it }
    )

    // Preview and full renders share one lock so two big bitmaps are never built at once.
    private val renderLock = Mutex()

    // Last full-size render, reused while the images and options are unchanged.
    private var exported: Pair<Pair<List<String>, CombineOptions>, File>? = null

    init {
        viewModelScope.launch {
            val dropped = withContext(Dispatchers.IO) { restoreCopies() }
            if (dropped > 0) {
                _events.send(CombineEvent.Message(
                    if (dropped == 1) "One image was cleared from the cache, so it was removed"
                    else "$dropped images were cleared from the cache, so they were removed"
                ))
            }
        }
        viewModelScope.launch {
            combine(paths, options) { p, o -> p to o }.debounce(150).collectLatest { (p, o) ->
                loadThumbs(p)
                renderPreview(p, o)
            }
        }
    }

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            val room = MAX_IMAGES - paths.value.size
            val copied = withContext(Dispatchers.IO) {
                uris.take(room.coerceAtLeast(0)).mapNotNull { copyToCache(it) }
            }
            _importing.value = false
            savedState[KEY_PATHS] = ArrayList(paths.value + copied.map { it.absolutePath })
            when {
                uris.size > room -> _events.send(CombineEvent.Message("You can combine up to $MAX_IMAGES images, so the rest were left out"))
                copied.size < uris.size -> _events.send(CombineEvent.Message("Some images couldn't be read"))
            }
        }
    }

    fun move(index: Int, delta: Int) {
        val list = paths.value.toMutableList()
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(index))
        savedState[KEY_PATHS] = ArrayList(list)
    }

    fun remove(index: Int) {
        val list = paths.value.toMutableList()
        if (index !in list.indices) return
        val removed = list.removeAt(index)
        savedState[KEY_PATHS] = ArrayList(list)
        _thumbs.update { it - removed }
        viewModelScope.launch(Dispatchers.IO) { File(removed).delete() }
    }

    fun setVertical(v: Boolean) { savedState[KEY_VERTICAL] = v }
    fun setSpacing(px: Int) { savedState[KEY_SPACING] = px.coerceIn(0, MAX_SPACING) }
    fun setBackground(color: Int) { savedState[KEY_BG] = color }
    fun setAlign(a: Align) { savedState[KEY_ALIGN] = a.name }
    fun setMatchSize(m: Boolean) { savedState[KEY_MATCH] = m }

    fun run(action: CombineAction) {
        if (_busy.value != null) return
        val p = paths.value
        val o = options.value
        if (p.size < MIN_IMAGES) return
        viewModelScope.launch {
            _busy.value = action
            try {
                val file = fullRender(p, o)
                when (action) {
                    CombineAction.UPLOAD -> _events.send(CombineEvent.Upload(file.absolutePath))
                    CombineAction.SHARE -> _events.send(CombineEvent.Share(file))
                    CombineAction.SAVE -> {
                        val ok = withContext(Dispatchers.IO) { ToolOutput.saveToPictures(context, file) }
                        _events.send(CombineEvent.Message(if (ok) "Saved to Pictures/XerahS" else "Couldn't save to Pictures/XerahS"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _events.send(CombineEvent.Message(TOO_BIG))
            } catch (e: Exception) {
                _events.send(CombineEvent.Message("Couldn't combine these images"))
            } finally {
                _busy.value = null
            }
        }
    }

    private suspend fun fullRender(p: List<String>, o: CombineOptions): File {
        val key = p to o
        exported?.let { (k, f) -> if (k == key && f.exists()) return f }
        val file = renderLock.withLock {
            withContext(Dispatchers.Default) {
                val bmp = CombineRenderer.render(context, p.map(::File), o, CombineRenderer.fullEdge(context))
                try {
                    // Opaque output compresses far better as JPEG; keep PNG when the background is see-through.
                    if (Color.alpha(o.background) == 255) {
                        ToolOutput.writeToCache(context, bmp, "combined", Bitmap.CompressFormat.JPEG, 92)
                    } else {
                        ToolOutput.writeToCache(context, bmp, "combined")
                    }
                } finally {
                    bmp.recycle()
                }
            }
        }
        exported = key to file
        return file
    }

    private suspend fun renderPreview(p: List<String>, o: CombineOptions) {
        if (p.size < MIN_IMAGES) {
            _preview.value = null
            return
        }
        _rendering.value = true
        try {
            val bmp = renderLock.withLock {
                withContext(Dispatchers.Default) {
                    CombineRenderer.render(context, p.map(::File), o, CombineRenderer.PREVIEW_EDGE)
                }
            }
            _preview.value = bmp
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            _preview.value = null
            _events.send(CombineEvent.Message(TOO_BIG))
        } catch (e: Exception) {
            _preview.value = null
            _events.send(CombineEvent.Message("Couldn't read one of the images"))
        } finally {
            _rendering.value = false
        }
    }

    private suspend fun loadThumbs(p: List<String>) {
        val missing = p.filter { it !in _thumbs.value }
        if (missing.isEmpty()) return
        val loaded = withContext(Dispatchers.IO) {
            missing.mapNotNull { path ->
                runCatching { ImageLoading.load(context, Uri.fromFile(File(path)), maxSide = 256) }.getOrNull()?.let { path to it }
            }
        }
        _thumbs.update { current -> (current + loaded).filterKeys { it in paths.value } }
    }

    private fun copyToCache(uri: Uri): File? = runCatching {
        inputDir.mkdirs()
        val out = File(inputDir, UUID.randomUUID().toString())
        val stream = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        stream.use { input -> out.outputStream().use { input.copyTo(it) } }
        if (ImageLoading.orientedSize(context, Uri.fromFile(out)) == null) {
            out.delete()
            null
        } else out
    }.getOrNull()

    // Drops saved paths whose copies are gone (the system can clear cacheDir while the app is in the
    // background) and stray files in our own folder. Returns how many saved paths were dropped.
    private fun restoreCopies(): Int {
        val saved = paths.value
        val present = saved.filter { File(it).isFile }
        if (present.size != saved.size) savedState[KEY_PATHS] = ArrayList(present)
        val keep = present.toSet()
        inputDir.listFiles()?.forEach { if (it.absolutePath !in keep) it.delete() }
        return saved.size - present.size
    }

    override fun onCleared() {
        // Leaving the tool: only this ViewModel's copies go. Other folders under tools_combine and
        // the exported render in cacheDir/tools_out are left to the app's cache sweep.
        val dir = inputDir
        Thread { dir.deleteRecursively() }.start()
    }

    companion object {
        const val MIN_IMAGES = 2
        const val MAX_IMAGES = 20
        const val MAX_SPACING = 100
        private const val TOO_BIG = "That's too big to combine on this phone. Try fewer or smaller images."
        const val INPUT_ROOT = "tools_combine"
        private const val KEY_DIR = "combine_dir"
        private const val KEY_PATHS = "combine_paths"
        private const val KEY_VERTICAL = "combine_vertical"
        private const val KEY_SPACING = "combine_spacing"
        private const val KEY_BG = "combine_bg"
        private const val KEY_ALIGN = "combine_align"
        private const val KEY_MATCH = "combine_match"
    }
}

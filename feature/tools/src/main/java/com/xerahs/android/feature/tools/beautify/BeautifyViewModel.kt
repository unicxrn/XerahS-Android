package com.xerahs.android.feature.tools.beautify

import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BeautifyEvent {
    data class Upload(val path: String) : BeautifyEvent
    data class Share(val file: File) : BeautifyEvent
    data class Message(val text: String) : BeautifyEvent
}

enum class BeautifyAction { UPLOAD, SHARE, SAVE }

/**
 * The picked (or handed over) image is copied into cacheDir straight away, and the copy's path
 * plus the options live in [SavedStateHandle] so they survive rotation and process death. The
 * preview is a downscaled render on a debounced job; the full-size render only happens for
 * Upload, Share and Save.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class BeautifyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val path: StateFlow<String?> = savedState.getStateFlow<String?>(KEY_PATH, null)
    val options: StateFlow<BeautifyOptions> = combine(
        savedState.getStateFlow(KEY_PADDING, BeautifyOptions().paddingPct),
        savedState.getStateFlow(KEY_BG, encode(BeautifyOptions().background)),
        savedState.getStateFlow(KEY_RADIUS, BeautifyOptions().cornerRadiusPct),
        savedState.getStateFlow(KEY_SHADOW, true),
        savedState.getStateFlow(KEY_ASPECT, Aspect.AUTO.name),
    ) { padding, bg, radius, shadow, aspect ->
        BeautifyOptions(padding, decode(bg), radius, shadow, Aspect.valueOf(aspect))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BeautifyOptions())

    private val accent = MutableStateFlow<Int?>(null)

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    private val _rendering = MutableStateFlow(false)
    val rendering: StateFlow<Boolean> = _rendering.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _busy = MutableStateFlow<BeautifyAction?>(null)
    val busy: StateFlow<BeautifyAction?> = _busy.asStateFlow()

    private val _events = Channel<BeautifyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val inputDir = File(context.cacheDir, "tools_beautify")

    // Small decoded copy of the source, reused for every preview render.
    private var previewSource: Pair<String, Bitmap>? = null

    // Last full-size render, reused while the image and options are unchanged.
    private var exported: Pair<Triple<String, BeautifyOptions, Int>, File>? = null

    init {
        val handed = savedState.get<String>(ARG_IMAGE_PATH)
        if (path.value == null && !handed.isNullOrBlank()) {
            viewModelScope.launch { import { copyToCache(Uri.fromFile(File(handed))) } }
        } else {
            viewModelScope.launch(Dispatchers.IO) { dropStaleCopies() }
        }
        viewModelScope.launch {
            combine(path, options, accent.filterNotNull()) { p, o, a -> Triple(p, o, a) }
                .debounce(60)
                .collectLatest { (p, o, a) -> renderPreview(p, o, a) }
        }
    }

    fun pick(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch { import { copyToCache(uri) } }
    }

    fun setAccent(argb: Int) { accent.value = argb }
    fun setPadding(pct: Float) { savedState[KEY_PADDING] = pct.coerceIn(0f, MAX_PADDING) }
    fun setRadius(pct: Float) { savedState[KEY_RADIUS] = pct.coerceIn(0f, MAX_RADIUS) }
    fun setBackground(bg: Background) { savedState[KEY_BG] = encode(bg) }
    fun setShadow(on: Boolean) { savedState[KEY_SHADOW] = on }
    fun setAspect(a: Aspect) { savedState[KEY_ASPECT] = a.name }

    fun run(action: BeautifyAction) {
        if (_busy.value != null) return
        val p = path.value ?: return
        val a = accent.value ?: return
        val o = options.value
        viewModelScope.launch {
            _busy.value = action
            try {
                val file = fullRender(p, o, a)
                when (action) {
                    BeautifyAction.UPLOAD -> _events.send(BeautifyEvent.Upload(file.absolutePath))
                    BeautifyAction.SHARE -> _events.send(BeautifyEvent.Share(file))
                    BeautifyAction.SAVE -> {
                        val ok = withContext(Dispatchers.IO) { ToolOutput.saveToPictures(context, file) }
                        _events.send(BeautifyEvent.Message(if (ok) "Saved to Pictures/XerahS" else "Couldn't save to Pictures/XerahS"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _events.send(BeautifyEvent.Message(TOO_BIG))
            } catch (e: Exception) {
                _events.send(BeautifyEvent.Message("Couldn't render this image"))
            } finally {
                _busy.value = null
            }
        }
    }

    private suspend fun import(copy: () -> File?) {
        _importing.value = true
        val file = withContext(Dispatchers.IO) { copy() }
        _importing.value = false
        if (file == null) {
            _events.send(BeautifyEvent.Message("Couldn't read that image"))
            return
        }
        val old = path.value
        savedState[KEY_PATH] = file.absolutePath
        if (old != null && old != file.absolutePath) withContext(Dispatchers.IO) { File(old).delete() }
    }

    private suspend fun fullRender(p: String, o: BeautifyOptions, accent: Int): File {
        val key = Triple(p, o, accent)
        exported?.let { (k, f) -> if (k == key && f.exists()) return f }
        val file = withContext(Dispatchers.Default) {
            // The output is capped at FULL_EDGE and the image sits inside it, so a source decoded
            // to that long edge is all the detail the render can use.
            val src = ImageLoading.load(context, Uri.fromFile(File(p)), maxSide = FULL_EDGE)
                ?: throw IllegalArgumentException("Can't read the image")
            val bmp = try {
                render(src, o, accent, FULL_EDGE)
            } finally {
                src.recycle()
            }
            try {
                ToolOutput.writeToCache(context, bmp, "beautified", o.outputFormat(), JPEG_QUALITY)
            } finally {
                bmp.recycle()
            }
        }
        exported = key to file
        return file
    }

    private suspend fun renderPreview(p: String?, o: BeautifyOptions, accent: Int) {
        if (p == null) {
            _preview.value = null
            return
        }
        _rendering.value = true
        try {
            _preview.value = withContext(Dispatchers.Default) {
                val src = previewSource?.takeIf { it.first == p }?.second
                    ?: (ImageLoading.load(context, Uri.fromFile(File(p)), maxSide = PREVIEW_EDGE)
                        ?: throw IllegalArgumentException("Can't read the image")).also { previewSource = p to it }
                render(src, o, accent, PREVIEW_EDGE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            _preview.value = null
            _events.send(BeautifyEvent.Message(TOO_BIG))
        } catch (e: Exception) {
            _preview.value = null
            _events.send(BeautifyEvent.Message("Couldn't read that image"))
        } finally {
            _rendering.value = false
        }
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

    private fun dropStaleCopies() {
        val keep = path.value
        inputDir.listFiles()?.forEach { if (it.absolutePath != keep) it.delete() }
    }

    override fun onCleared() {
        // The exported render stays in cacheDir/tools_out because Upload may still be reading it.
        val dir = inputDir
        Thread { dir.deleteRecursively() }.start()
    }

    companion object {
        const val ARG_IMAGE_PATH = "imagePath"
        const val MAX_PADDING = 0.25f
        const val MAX_RADIUS = 0.12f
        private const val PREVIEW_EDGE = 1080
        private const val FULL_EDGE = 4096
        private const val JPEG_QUALITY = 92
        private const val TOO_BIG = "That image is too big to beautify on this phone."
        private const val KEY_PATH = "beautify_path"
        private const val KEY_PADDING = "beautify_padding"
        private const val KEY_BG = "beautify_bg"
        private const val KEY_RADIUS = "beautify_radius"
        private const val KEY_SHADOW = "beautify_shadow"
        private const val KEY_ASPECT = "beautify_aspect"

        private fun encode(bg: Background): String = when (bg) {
            is Background.Gradient -> "g:${bg.preset}"
            is Background.Solid -> "s:${bg.argb}"
            Background.Accent -> "accent"
            Background.Transparent -> "none"
        }

        private fun decode(s: String): Background = when {
            s.startsWith("g:") -> Background.Gradient(s.removePrefix("g:").toIntOrNull() ?: 0)
            s.startsWith("s:") -> Background.Solid(s.removePrefix("s:").toIntOrNull() ?: 0xFFFFFFFF.toInt())
            s == "accent" -> Background.Accent
            s == "none" -> Background.Transparent
            else -> Background.Gradient(0)
        }
    }
}

/** Opaque backgrounds go out as JPEG, transparent ones as PNG. */
internal fun BeautifyOptions.outputFormat(): Bitmap.CompressFormat =
    if (needsAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

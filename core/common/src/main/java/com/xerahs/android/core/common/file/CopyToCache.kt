package com.xerahs.android.core.common.file

import android.content.ContentResolver
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** What [copyToCacheFrom] needs from a content source, so the copy logic can be tested without Android. */
interface CopySource {
    fun displayName(): String?
    /** The MIME type of the bytes that [open] returns (for a virtual document, its stream type). */
    fun mimeType(): String?
    /** Size in bytes if the provider reports it. */
    fun size(): Long? = null
    fun open(): InputStream?
}

class InsufficientStorageException : IOException("Not enough storage to copy this file")

private const val MAX_NAME_BYTES = 200
private const val MAX_EXT_CHARS = 16
private const val STORAGE_MARGIN = 50L * 1024 * 1024

/**
 * Copies [uri] into [dir], keeping the real extension (see [fileNameFor]). Closes every stream.
 * Runs on [Dispatchers.IO]. Throws if the source cannot be read or the copy would not fit;
 * a partial file is deleted first.
 */
suspend fun copyToCache(resolver: ContentResolver, uri: Uri, dir: File): File =
    withContext(Dispatchers.IO) {
        dir.mkdirs()
        val source = ResolverSource(resolver, uri)
        val available = runCatching { StatFs(dir.path).availableBytes - STORAGE_MARGIN }.getOrDefault(Long.MAX_VALUE)
        copyToCacheFrom(source, dir, available) { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    }

private class ResolverSource(private val resolver: ContentResolver, private val uri: Uri) : CopySource {
    private val rawType: String? = runCatching { resolver.getType(uri) }.getOrNull()

    private val virtualType: String? by lazy {
        runCatching {
            val flags = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } ?: 0
            if (flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT == 0) null
            else resolver.getStreamTypes(uri, "*/*")?.firstOrNull()
        }.getOrNull()
    }

    override fun displayName(): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment

    override fun mimeType(): String? = virtualType ?: rawType

    override fun size(): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
    }.getOrNull()?.takeIf { it >= 0 }
        ?: runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()?.takeIf { it >= 0 }

    override fun open(): InputStream? {
        val type = virtualType
        return if (type != null) resolver.openTypedAssetFileDescriptor(uri, type, null)?.createInputStream()
        else resolver.openInputStream(uri)
    }
}

/** [availableBytes] is the free space left for the copy (after any margin); the copy fails up front if the source is bigger. */
fun copyToCacheFrom(
    source: CopySource,
    dir: File,
    availableBytes: Long = Long.MAX_VALUE,
    extensionForMime: (String) -> String? = { null }
): File {
    dir.mkdirs()
    source.size()?.let { if (it > availableBytes) throw InsufficientStorageException() }
    val file = File(dir, fileNameFor(source.displayName(), source.mimeType(), extensionForMime))
    try {
        val input = source.open() ?: throw FileNotFoundException("Cannot open source")
        input.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
    } catch (e: Exception) {
        file.delete()
        throw e
    }
    return file
}

/**
 * Keeps the display name's extension only if [MimeTypes] knows it, or the MIME type is missing or
 * octet-stream. Otherwise the MIME type decides ([MimeTypes] first, then [extensionForMime]); with
 * no match the name's own extension survives if it looks real, else ".bin".
 */
fun fileNameFor(displayName: String?, mimeType: String?, extensionForMime: (String) -> String? = { null }): String {
    var name = (displayName ?: "shared").substringAfterLast('/')
        .filter { !it.isISOControl() }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .trimEnd('.', ' ')
    if (name.isBlank()) name = "shared"
    val dot = name.lastIndexOf('.')
    val ext = if (dot > 0) name.substring(dot + 1) else ""
    val mime = mimeType?.lowercase()?.substringBefore(';')?.trim()
    val mimeUnknown = mime == null || mime == MimeTypes.OCTET_STREAM
    val keep = ext.isNotEmpty() && ext.length <= MAX_EXT_CHARS && (MimeTypes.isKnownExtension(ext) || mimeUnknown)
    if (!keep) {
        val fromMime = mime?.takeUnless { mimeUnknown }?.let { MimeTypes.extensionFor(it) ?: extensionForMime(it) }
        val looksReal = ext.length in 1..8 && ext.any { it.isLetter() } && ext.all { it.isLetterOrDigit() }
        name = when {
            fromMime != null -> "$name.${fromMime.take(MAX_EXT_CHARS)}"
            looksReal -> name
            else -> "$name.bin"
        }
    }
    return limitFileName(name)
}

/** Keeps the UTF-8 length within [MAX_NAME_BYTES] by trimming the base name, never the extension. */
private fun limitFileName(name: String): String {
    val dot = name.lastIndexOf('.')
    val ext = if (dot >= 0) name.substring(dot) else ""
    var base = if (dot >= 0) name.substring(0, dot) else name
    val budget = (MAX_NAME_BYTES - ext.toByteArray(Charsets.UTF_8).size).coerceAtLeast(1)
    while (base.isNotEmpty() && base.toByteArray(Charsets.UTF_8).size > budget) {
        base = base.dropLast(1)
        if (base.isNotEmpty() && base.last().isHighSurrogate()) base = base.dropLast(1)
    }
    if (base.isBlank()) base = "shared"
    return base + ext
}

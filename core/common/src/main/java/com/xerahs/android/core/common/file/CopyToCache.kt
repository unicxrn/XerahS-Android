package com.xerahs.android.core.common.file

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/** What [copyToCacheFrom] needs from a content source, so the copy logic can be tested without Android. */
interface CopySource {
    fun displayName(): String?
    fun mimeType(): String?
    fun open(): InputStream?
}

private const val MAX_NAME_BYTES = 200

/**
 * Copies [uri] into [dir], keeping the real extension (from the display name, else the MIME type,
 * else ".bin"). Closes every stream. Runs on [Dispatchers.IO]. Throws if the source cannot be read;
 * a partial file is deleted first.
 */
suspend fun copyToCache(resolver: ContentResolver, uri: Uri, dir: File): File =
    withContext(Dispatchers.IO) {
        copyToCacheFrom(object : CopySource {
            override fun displayName(): String? = runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment
            override fun mimeType(): String? = runCatching { resolver.getType(uri) }.getOrNull()
            override fun open(): InputStream? = resolver.openInputStream(uri)
        }, dir)
    }

fun copyToCacheFrom(source: CopySource, dir: File): File {
    dir.mkdirs()
    val file = File(dir, fileNameFor(source.displayName(), source.mimeType()))
    try {
        val input = source.open() ?: throw FileNotFoundException("Cannot open source")
        input.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
    } catch (e: Exception) {
        file.delete()
        throw e
    }
    return file
}

fun fileNameFor(displayName: String?, mimeType: String?): String {
    var name = (displayName ?: "shared").substringAfterLast('/').replace(Regex("""[\\/:*?"<>|]"""), "_")
    if (name.isBlank()) name = "shared"
    if (!name.contains('.') || name.endsWith('.')) {
        val ext = mimeType?.let { MimeTypes.extensionFor(it) } ?: "bin"
        name = name.trimEnd('.') + "." + ext
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

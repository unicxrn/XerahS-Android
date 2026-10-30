package com.xerahs.android.feature.upload.intake

import com.xerahs.android.core.common.file.fileNameFor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.Charset

/** Largest file "Upload from link" will download. */
const val MAX_LINK_DOWNLOAD_BYTES = 200L * 1024 * 1024

private const val MB = 1024L * 1024
private const val FALLBACK_NAME = "download"

class FileTooLargeException(maxBytes: Long) : IOException(
    "That file is larger than " + if (maxBytes >= MB) "${maxBytes / MB} MB" else "$maxBytes bytes"
)

/** Throws [FileTooLargeException] once [bytes] goes past [maxBytes]. */
fun checkDownloadSize(bytes: Long, maxBytes: Long) {
    if (bytes > maxBytes) throw FileTooLargeException(maxBytes)
}

/**
 * The text as a link when it is exactly one http(s) URL (surrounding spaces are fine,
 * anything else around or inside it is not). Otherwise null.
 */
fun singleUrlOrNull(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) return null
    return trimmed.takeIf { it.toHttpUrlOrNull() != null }
}

/**
 * Picks a safe file name for a download: Content-Disposition `filename*=` first, then `filename=`,
 * then the URL's last path segment. Any folder part and leading dots are dropped, and an extension
 * from [contentType] is added when the name has none that fits.
 */
fun resolveFileName(url: String, contentDisposition: String?, contentType: String?): String {
    val raw = contentDisposition?.let { extendedFilename(it) ?: plainFilename(it) }
        ?: url.toHttpUrlOrNull()?.pathSegments?.lastOrNull()
    val base = raw?.let(::sanitize)?.takeIf { it.isNotBlank() } ?: FALLBACK_NAME
    val mime = contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    return fileNameFor(base, mime)
}

private val EXTENDED = Regex("""(?i)(?:^|;)\s*filename\*\s*=\s*"?([^;"]+)"?""")
private val PLAIN = Regex("""(?i)(?:^|;)\s*filename\s*=\s*(?:"((?:[^"\\]|\\.)*)"|([^;]*))""")

/** RFC 5987 value: `charset'language'percent-encoded`. */
private fun extendedFilename(header: String): String? {
    val value = EXTENDED.find(header)?.groupValues?.get(1)?.trim() ?: return null
    val parts = value.split('\'', limit = 3)
    if (parts.size != 3) return null
    val charset = runCatching { Charset.forName(parts[0].ifBlank { "UTF-8" }) }.getOrNull() ?: Charsets.UTF_8
    // URLDecoder treats '+' as a space; RFC 5987 does not.
    return runCatching { URLDecoder.decode(parts[2].replace("+", "%2B"), charset.name()) }.getOrNull()
        ?.takeIf { it.isNotBlank() }
}

private fun plainFilename(header: String): String? {
    val m = PLAIN.find(header) ?: return null
    val quoted = m.groups[1]?.value?.replace(Regex("""\\(.)"""), "$1")
    return (quoted ?: m.groups[2]?.value)?.trim()?.takeIf { it.isNotBlank() }
}

private fun sanitize(name: String): String =
    name.substringAfterLast('/')
        .substringAfterLast('\\')
        .filter { !it.isISOControl() }
        .replace("..", ".")
        .trim()
        .trimStart('.')
        .trim()

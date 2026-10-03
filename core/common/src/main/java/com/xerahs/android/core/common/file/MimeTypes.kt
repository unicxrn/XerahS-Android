package com.xerahs.android.core.common.file

/** Extension-based MIME helpers. Pure Kotlin so it is unit-testable (no android.webkit). */
object MimeTypes {
    const val OCTET_STREAM = "application/octet-stream"

    // Order matters: the first extension listed for a MIME type is its canonical one.
    private val byExtension = linkedMapOf(
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp",
        "heic" to "image/heic", "heif" to "image/heif", "svg" to "image/svg+xml",
        "mp4" to "video/mp4", "webm" to "video/webm", "mkv" to "video/x-matroska",
        "mov" to "video/quicktime", "mp3" to "audio/mpeg", "ogg" to "audio/ogg",
        "wav" to "audio/wav", "m4a" to "audio/mp4",
        "txt" to "text/plain", "log" to "text/plain", "md" to "text/markdown",
        "csv" to "text/csv", "html" to "text/html", "json" to "application/json",
        "xml" to "application/xml", "pdf" to "application/pdf", "zip" to "application/zip",
        "apk" to "application/vnd.android.package-archive",
    )

    fun fromFileName(name: String): String =
        byExtension[name.substringAfterLast('.', "").lowercase()] ?: OCTET_STREAM

    fun extensionFor(mimeType: String): String? =
        byExtension.entries.firstOrNull { it.value == mimeType }?.key

    fun isRasterImage(mimeType: String?): Boolean =
        mimeType != null && mimeType.startsWith("image/") && mimeType != "image/svg+xml"

    fun isHeic(mimeType: String?): Boolean = mimeType == "image/heic" || mimeType == "image/heif"

    fun isText(mimeType: String?): Boolean = mimeType != null &&
        (mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "application/xml")
}

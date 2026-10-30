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
        "avif" to "image/avif", "tiff" to "image/tiff", "tif" to "image/tiff", "ico" to "image/x-icon",
        "dng" to "image/x-adobe-dng", "3gp" to "video/3gpp", "3gpp" to "video/3gpp",
        "flac" to "audio/flac", "aac" to "audio/aac", "opus" to "audio/opus",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "rtf" to "application/rtf", "7z" to "application/x-7z-compressed",
        "rar" to "application/vnd.rar", "tar" to "application/x-tar", "gz" to "application/gzip",
    )

    fun isKnownExtension(ext: String): Boolean = byExtension.containsKey(ext.lowercase())

    fun fromFileName(name: String): String =
        byExtension[name.substringAfterLast('.', "").lowercase()] ?: OCTET_STREAM

    fun extensionFor(mimeType: String): String? =
        byExtension.entries.firstOrNull { it.value == mimeType }?.key

    fun isRasterImage(mimeType: String?): Boolean =
        mimeType != null && mimeType.startsWith("image/") && mimeType != "image/svg+xml"

    /** Whether the annotation editor can open this MIME type. GIFs would lose their animation, so they go to Upload. */
    fun opensInEditor(mimeType: String?): Boolean =
        isRasterImage(mimeType) && !isHeic(mimeType) && mimeType != "image/gif"

    fun isHeic(mimeType: String?): Boolean = mimeType == "image/heic" || mimeType == "image/heif"

    fun isText(mimeType: String?): Boolean = mimeType != null &&
        (mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "application/xml")
}

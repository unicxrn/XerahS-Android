package com.xerahs.android.feature.upload.uploader

/**
 * Builds the public link for an FTP or SFTP upload.
 *
 * The HTTP URL serves the fixed part of Remote Path: every folder before the first one
 * with a date token. Folders from that point on (such as `{yyyy}/{MM}`) are added to the
 * link from [resolvedPath], which must be the same resolved path the upload went to.
 */
internal object RemoteLink {

    fun build(
        httpUrl: String,
        rawPath: String,
        resolvedPath: String,
        fileName: String,
        fallbackBase: String
    ): String {
        val rawSegments = segments(rawPath)
        val resolvedSegments = segments(resolvedPath)
        val fixedCount = rawSegments.indexOfFirst { '{' in it }.let { if (it < 0) rawSegments.size else it }
        val pathPart = if (httpUrl.isNotBlank()) resolvedSegments.drop(fixedCount) else resolvedSegments
        val base = if (httpUrl.isNotBlank()) httpUrl.trim().trimEnd('/') else fallbackBase.trimEnd('/')
        return (listOf(base) + (pathPart + fileName).map(::encodeSegment)).joinToString("/")
    }

    private fun segments(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    /** Percent-encodes one path segment, keeping only RFC 3986 unreserved characters. */
    fun encodeSegment(segment: String): String {
        val out = StringBuilder()
        for (byte in segment.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
                out.append(ch)
            } else {
                out.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
            }
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}

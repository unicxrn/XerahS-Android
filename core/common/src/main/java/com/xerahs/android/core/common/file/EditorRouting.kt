package com.xerahs.android.core.common.file

import java.io.File

object EditorRouting {
    /** True for a WebP file whose header says it is animated (VP8X chunk with the animation flag). */
    fun isAnimatedWebp(header: ByteArray): Boolean =
        header.size >= 21 &&
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP" &&
            String(header, 12, 4, Charsets.US_ASCII) == "VP8X" &&
            (header[20].toInt() and 0x02) != 0

    /** Decides by file name and, for WebP, by header: whether the editor should open this file. */
    fun opensInEditor(path: String): Boolean {
        val mime = MimeTypes.fromFileName(path)
        if (!MimeTypes.opensInEditor(mime)) return false
        if (mime == "image/webp") {
            val header = runCatching {
                File(path).inputStream().use { i -> ByteArray(32).also { b -> i.read(b) } }
            }.getOrNull()
            if (header != null && isAnimatedWebp(header)) return false
        }
        return true
    }
}

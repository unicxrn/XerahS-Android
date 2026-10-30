package com.xerahs.android.core.domain

import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.domain.model.FileKind
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.LinkFormat
import com.xerahs.android.core.domain.model.fileKindOf
import java.net.URLDecoder

object LinkFormatter {

    /** Writes [url] in [format]. Image formats fall back to the plain link form when [isImage] is false. */
    fun format(url: String, fileName: String, isImage: Boolean, format: LinkFormat): String = when (format) {
        LinkFormat.PLAIN -> url
        LinkFormat.MARKDOWN -> "[${mdText(fileName)}](${mdUrl(url)})"
        LinkFormat.MARKDOWN_IMAGE -> if (isImage) "![${mdText(fileName)}](${mdUrl(url)})" else format(url, fileName, false, LinkFormat.MARKDOWN)
        LinkFormat.HTML -> "<a href=\"${html(url)}\">${html(fileName)}</a>"
        LinkFormat.HTML_IMAGE -> if (isImage) "<img src=\"${html(url)}\" alt=\"${html(fileName)}\">" else format(url, fileName, false, LinkFormat.HTML)
        LinkFormat.BBCODE -> "[url=${bbUrl(url)}]${bbText(fileName)}[/url]"
        LinkFormat.BBCODE_IMAGE -> if (isImage) "[img]${bbUrl(url)}[/img]" else format(url, fileName, false, LinkFormat.BBCODE)
    }

    /**
     * Formats one link per line. A single link uses [fileName] and [isImage] when given; otherwise
     * (and for every line of a batch) both come from the last path segment of the URL.
     */
    fun formatLinks(text: String, format: LinkFormat, fileName: String? = null, isImage: Boolean? = null): String {
        if (format == LinkFormat.PLAIN) return text
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val single = lines.size == 1
        return lines.joinToString("\n") { url ->
            val name = fileName?.takeIf { single && it.isNotBlank() } ?: nameFromUrl(url)
            val image = isImage?.takeIf { single } ?: isImageMime(MimeTypes.fromFileName(name))
            format(url, name, image, format)
        }
    }

    /**
     * The text a copy button puts on the clipboard for [item]: [link] (the upload URL, or a short
     * link) in [format]. Local files have no link and copy their path as is.
     */
    fun forItem(item: HistoryItem, format: LinkFormat, link: String? = item.url): String =
        link?.takeIf { it.isNotBlank() }?.let { formatLinks(it, format, item.fileName, item.isImageLink) } ?: item.filePath

    /** Any image type, SVG included, gets the image forms. */
    fun isImageMime(mimeType: String?): Boolean = fileKindOf(mimeType) == FileKind.IMAGE

    /** Last path segment without query or fragment, percent-decoded when that works. */
    fun nameFromUrl(url: String): String {
        val raw = url.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/')
        val decoded = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
        return decoded.ifBlank { url }
    }

    private fun mdText(s: String) = s.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")

    private fun mdUrl(s: String) = s.replace("(", "%28").replace(")", "%29").replace(" ", "%20")

    private fun bbText(s: String) = s.replace("[", "").replace("]", "")

    private fun bbUrl(s: String) = s.replace("[", "%5B").replace("]", "%5D")

    private fun html(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

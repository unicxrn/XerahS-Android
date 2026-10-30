package com.xerahs.android.core.domain

import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.domain.model.LinkFormat

object LinkFormatter {

    /** Writes [url] in [format]. Image formats fall back to the plain link form when [isImage] is false. */
    fun format(url: String, fileName: String, isImage: Boolean, format: LinkFormat): String = when (format) {
        LinkFormat.PLAIN -> url
        LinkFormat.MARKDOWN -> "[${md(fileName)}]($url)"
        LinkFormat.MARKDOWN_IMAGE -> if (isImage) "![${md(fileName)}]($url)" else format(url, fileName, false, LinkFormat.MARKDOWN)
        LinkFormat.HTML -> "<a href=\"${html(url)}\">${html(fileName)}</a>"
        LinkFormat.HTML_IMAGE -> if (isImage) "<img src=\"${html(url)}\" alt=\"${html(fileName)}\">" else format(url, fileName, false, LinkFormat.HTML)
        LinkFormat.BBCODE -> "[url=$url]$fileName[/url]"
        LinkFormat.BBCODE_IMAGE -> if (isImage) "[img]$url[/img]" else format(url, fileName, false, LinkFormat.BBCODE)
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
            val image = isImage?.takeIf { single } ?: MimeTypes.isRasterImage(MimeTypes.fromFileName(name))
            format(url, name, image, format)
        }
    }

    private fun nameFromUrl(url: String): String =
        url.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/').ifBlank { url }

    private fun md(s: String) = s.replace("[", "\\[").replace("]", "\\]")

    private fun html(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

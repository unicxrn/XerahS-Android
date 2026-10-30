package com.xerahs.android.core.domain.model

/** How a link is written when copied. Image variants only apply to images. */
enum class LinkFormat(val label: String) {
    PLAIN("Plain URL"),
    MARKDOWN("Markdown"),
    MARKDOWN_IMAGE("Markdown image"),
    HTML("HTML"),
    HTML_IMAGE("HTML image"),
    BBCODE("BBCode"),
    BBCODE_IMAGE("BBCode image");

    companion object {
        fun fromName(name: String?): LinkFormat = entries.firstOrNull { it.name == name } ?: PLAIN
    }
}

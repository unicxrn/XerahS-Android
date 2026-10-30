package com.xerahs.android.core.domain

import com.xerahs.android.core.domain.model.LinkFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkFormatterTest {
    private val url = "https://x/a.png"

    private fun fmt(format: LinkFormat, isImage: Boolean = true, name: String = "a.png") =
        LinkFormatter.format(url, name, isImage, format)

    @Test fun plain() = assertEquals(url, fmt(LinkFormat.PLAIN))
    @Test fun markdown() = assertEquals("[a.png](https://x/a.png)", fmt(LinkFormat.MARKDOWN))
    @Test fun markdownImage() = assertEquals("![a.png](https://x/a.png)", fmt(LinkFormat.MARKDOWN_IMAGE))
    @Test fun html() = assertEquals("<a href=\"https://x/a.png\">a.png</a>", fmt(LinkFormat.HTML))
    @Test fun htmlImage() = assertEquals("<img src=\"https://x/a.png\" alt=\"a.png\">", fmt(LinkFormat.HTML_IMAGE))
    @Test fun bbcode() = assertEquals("[url=https://x/a.png]a.png[/url]", fmt(LinkFormat.BBCODE))
    @Test fun bbcodeImage() = assertEquals("[img]https://x/a.png[/img]", fmt(LinkFormat.BBCODE_IMAGE))

    @Test fun imageFormatsFallBackToLinksForOtherFiles() {
        assertEquals("[a.png](https://x/a.png)", fmt(LinkFormat.MARKDOWN_IMAGE, isImage = false))
        assertEquals("<a href=\"https://x/a.png\">a.png</a>", fmt(LinkFormat.HTML_IMAGE, isImage = false))
        assertEquals("[url=https://x/a.png]a.png[/url]", fmt(LinkFormat.BBCODE_IMAGE, isImage = false))
    }

    @Test fun htmlEscapesFileName() {
        assertEquals(
            "<a href=\"https://x/a.png\">&lt;b&gt; &amp; &quot;q&quot;</a>",
            fmt(LinkFormat.HTML, name = "<b> & \"q\"")
        )
        assertEquals(
            "<img src=\"https://x/a.png\" alt=\"&lt;b&gt; &amp; &quot;q&quot;\">",
            fmt(LinkFormat.HTML_IMAGE, name = "<b> & \"q\"")
        )
    }

    @Test fun formatLinksUsesNameFromEachUrlInBatches() {
        assertEquals(
            "![a.png](https://x/a.png)\n[b.txt](https://x/b.txt)",
            LinkFormatter.formatLinks("https://x/a.png\nhttps://x/b.txt", LinkFormat.MARKDOWN_IMAGE)
        )
    }

    @Test fun formatLinksUsesGivenNameForSingleLink() {
        assertEquals(
            "[shot.png](https://i/abc)",
            LinkFormatter.formatLinks("https://i/abc", LinkFormat.MARKDOWN, fileName = "shot.png", isImage = false)
        )
    }

    @Test fun formatLinksPlainLeavesTextAlone() {
        assertEquals("https://x/a.png\nhttps://x/b", LinkFormatter.formatLinks("https://x/a.png\nhttps://x/b", LinkFormat.PLAIN))
    }
}

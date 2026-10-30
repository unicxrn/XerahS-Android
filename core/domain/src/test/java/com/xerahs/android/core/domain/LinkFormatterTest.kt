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

    @Test fun htmlEscapesQuoteAndAmpersandInUrl() {
        val u = "https://x/a?b=1&c=\"2\""
        assertEquals("<a href=\"https://x/a?b=1&amp;c=&quot;2&quot;\">a.png</a>", LinkFormatter.format(u, "a.png", true, LinkFormat.HTML))
        assertEquals("<img src=\"https://x/a?b=1&amp;c=&quot;2&quot;\" alt=\"a.png\">", LinkFormatter.format(u, "a.png", true, LinkFormat.HTML_IMAGE))
    }

    private val odd = "https://x/a (1)[2].png"

    @Test fun markdownEncodesParensAndSpaceInUrlAndEscapesText() {
        assertEquals("[a \\\\ \\[1\\].png](https://x/a%20%281%29[2].png)", LinkFormatter.format(odd, "a \\ [1].png", false, LinkFormat.MARKDOWN))
        assertEquals("![a.png](https://x/a%20%281%29[2].png)", LinkFormatter.format(odd, "a.png", true, LinkFormat.MARKDOWN_IMAGE))
    }

    @Test fun bbcodeEncodesBracketsInUrlAndStripsThemFromText() {
        assertEquals("[url=https://x/a (1)%5B2%5D.png]a 1.png[/url]", LinkFormatter.format(odd, "a [1].png", false, LinkFormat.BBCODE))
        assertEquals("[img]https://x/a (1)%5B2%5D.png[/img]", LinkFormatter.format(odd, "a.png", true, LinkFormat.BBCODE_IMAGE))
    }

    @Test fun nameFromUrlDropsQueryAndFragmentAndDecodes() {
        assertEquals("my shot.png", LinkFormatter.nameFromUrl("https://x/dir/my%20shot.png?sig=abc#top"))
        assertEquals("a.png", LinkFormatter.nameFromUrl("https://x/a.png#frag"))
        assertEquals("100%.png", LinkFormatter.nameFromUrl("https://x/100%.png"))
    }

    @Test fun svgCountsAsImageInBatches() {
        assertEquals(
            "![a.svg](https://x/a.svg)\n![b.png](https://x/b.png?x=1)",
            LinkFormatter.formatLinks("https://x/a.svg\nhttps://x/b.png?x=1", LinkFormat.MARKDOWN_IMAGE)
        )
    }
}

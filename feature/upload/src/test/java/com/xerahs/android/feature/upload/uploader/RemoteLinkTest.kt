package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.common.PathPattern
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class RemoteLinkTest {

    private val now = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 5, 23, 59, 59) }.time

    private fun link(httpUrl: String, raw: String, name: String = "a.png", fallback: String = "ftp://host") =
        RemoteLink.build(httpUrl, raw, PathPattern.resolve(raw, now).trimEnd('/'), name, fallback)

    @Test fun plainPathKeepsOldLink() =
        assertEquals("https://cdn.test/i/a.png", link("https://cdn.test/i/", "/var/www/i"))

    @Test fun dateFoldersAreAddedAfterBase() =
        assertEquals("https://cdn.test/i/2026/03/a.png", link("https://cdn.test/i", "/var/www/i/{yyyy}/{MM}"))

    @Test fun tokenInsideSegmentCountsAsDateFolder() =
        assertEquals("https://cdn.test/shots-2026/05/a.png", link("https://cdn.test", "/srv/shots-{yyyy}/{dd}"))

    @Test fun fixedFolderAfterTokenIsKept() =
        assertEquals("https://cdn.test/2026/img/a.png", link("https://cdn.test", "/www/{yyyy}/img"))

    @Test fun segmentsArePercentEncoded() =
        assertEquals("https://cdn.test/march/my%20shot%20%281%29.png",
            link("https://cdn.test", "/www/{month}", name = "my shot (1).png"))

    @Test fun nonAsciiNameIsUtf8Encoded() =
        assertEquals("https://cdn.test/caf%C3%A9.png", link("https://cdn.test", "/", name = "café.png"))

    @Test fun fallbackUsesWholeResolvedPath() =
        assertEquals("sftp://host/home/u/2026/a.png", link("", "/home/u/{yyyy}", fallback = "sftp://host"))

    @Test fun fallbackWithRelativePathStillHasSlash() =
        assertEquals("ftp://host/up/a.png", link("", "up"))

    @Test fun rootPathFallback() =
        assertEquals("ftp://host/a.png", link("", "/"))
}

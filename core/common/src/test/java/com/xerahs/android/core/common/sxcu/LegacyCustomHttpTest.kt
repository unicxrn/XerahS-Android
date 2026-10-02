package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyCustomHttpTest {
    @Test fun mapsOldFieldsToMultipartSpec() {
        val spec = LegacyCustomHttp.toSpec(
            url = "https://h.test/up", method = "put",
            headers = mapOf("X-Key" to "1"), responseUrlJsonPath = "data.url", formFieldName = "img"
        )
        assertEquals("Custom HTTP", spec.name)
        assertEquals("PUT", spec.requestMethod)
        assertEquals("https://h.test/up", spec.requestURL)
        assertEquals(mapOf("X-Key" to "1"), spec.headers)
        assertEquals(CustomBodyType.MULTIPART_FORM_DATA, spec.body)
        assertEquals("img", spec.fileFormName)
        assertEquals("{json:data.url}", spec.url)
    }

    @Test fun blankValuesFallBackToDefaults() {
        val spec = LegacyCustomHttp.toSpec("https://h.test", "", emptyMap(), "", "")
        assertEquals("POST", spec.requestMethod)
        assertEquals("file", spec.fileFormName)
        assertEquals("", spec.url)
    }

    @Test fun parsesHeaderLines() = assertEquals(
        mapOf("A" to "1", "B" to "x=y"),
        LegacyCustomHttp.parseHeaderLines("A=1\nnot a header\nB=x=y")
    )
}

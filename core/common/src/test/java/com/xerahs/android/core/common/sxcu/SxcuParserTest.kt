package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SxcuParserTest {
    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("sxcu/$name")!!.readText()

    @Test fun parsesMultipartImageUploader() {
        val spec = SxcuParser.parse(fixture("multipart_image.sxcu")).getOrThrow()
        assertEquals("My Host", spec.name)
        assertEquals(setOf(CustomDestinationType.IMAGE, CustomDestinationType.FILE), spec.destinationTypes)
        assertEquals("POST", spec.requestMethod)
        assertEquals("https://up.example.com/api/upload", spec.requestURL)
        assertEquals(mapOf("Authorization" to "Bearer abc123"), spec.headers)
        assertEquals(CustomBodyType.MULTIPART_FORM_DATA, spec.body)
        assertEquals(mapOf("album" to "{inputbox:Album|main}"), spec.arguments)
        assertEquals("image", spec.fileFormName)
        assertEquals("{json:data.url}", spec.url)
        assertEquals("{json:data.thumb}", spec.thumbnailURL)
        assertEquals("{json:data.delete}", spec.deletionURL)
        assertEquals("{json:error.message}", spec.errorMessage)
    }

    @Test fun parsesTextUploader() {
        val spec = SxcuParser.parse(fixture("text_urlencoded.sxcu")).getOrThrow()
        assertEquals(setOf(CustomDestinationType.TEXT), spec.destinationTypes)
        assertEquals(CustomBodyType.FORM_URL_ENCODED, spec.body)
        assertEquals("", spec.fileFormName)
    }

    @Test fun parsesJsonShortener() {
        val spec = SxcuParser.parse(fixture("shortener_json.sxcu")).getOrThrow()
        assertEquals(setOf(CustomDestinationType.URL_SHORTENER), spec.destinationTypes)
        assertEquals(CustomBodyType.JSON, spec.body)
        assertEquals("""{"url":"{input}"}""", spec.data)
    }

    @Test fun keysAreCaseInsensitiveAndMethodUppercased() {
        val spec = SxcuParser.parse(fixture("binary_put.sxcu")).getOrThrow()
        assertEquals("Binary PUT", spec.name)
        assertEquals("PUT", spec.requestMethod)
        assertEquals(CustomBodyType.BINARY, spec.body)
        assertEquals(mapOf("ttl" to "3600"), spec.parameters)
    }

    @Test fun convertsLegacyRequestTypeAndSyntax() {
        val spec = SxcuParser.parse(fixture("legacy_v12.sxcu")).getOrThrow()
        assertEquals("POST", spec.requestMethod)
        assertEquals(CustomBodyType.MULTIPART_FORM_DATA, spec.body)
        assertEquals("""{regex:"url":"(.+?)"|1}""", spec.url)
        assertEquals("{json:delete}", spec.deletionURL)
    }

    @Test fun nameFallsBackToHost() {
        val spec = SxcuParser.parse("""{"RequestURL":"https://h.example.com/u"}""").getOrThrow()
        assertEquals("h.example.com", spec.name)
        assertEquals(setOf(CustomDestinationType.IMAGE, CustomDestinationType.FILE), spec.destinationTypes)
        assertEquals(CustomBodyType.NONE, spec.body)
    }

    @Test fun rejectsInvalidInput() {
        assertTrue(SxcuParser.parse("not json").isFailure)
        assertTrue(SxcuParser.parse("""{"Name":"no url"}""").isFailure)
        assertTrue(SxcuParser.parse("""{"RequestURL":"https://x","Body":"Carrier pigeon"}""").isFailure)
    }

    @Test fun writerRoundTrips() {
        listOf("multipart_image.sxcu", "text_urlencoded.sxcu", "shortener_json.sxcu", "binary_put.sxcu").forEach {
            val spec = SxcuParser.parse(fixture(it)).getOrThrow()
            assertEquals(it, spec, SxcuParser.parse(SxcuWriter.write(spec)).getOrThrow())
        }
    }

    @Test fun writerKeepsEmptyRequestUrlParseable() {
        val spec = CustomUploaderSpec(requestURL = "")
        assertEquals(spec, SxcuParser.parse(SxcuWriter.write(spec)).getOrThrow())
    }
}

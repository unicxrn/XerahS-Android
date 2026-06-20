package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Test

class SxcuParserTest {
    @Test fun parsesFullConfig() {
        val json = """
            {
              "Version": "13.7.0",
              "Name": "My Host",
              "DestinationType": "ImageUploader",
              "RequestMethod": "POST",
              "RequestURL": "https://up.example.com/api/upload",
              "Headers": { "Authorization": "Bearer abc123" },
              "Body": "MultipartFormData",
              "FileFormName": "image",
              "URL": "${'$'}json:data.url${'$'}"
            }
        """.trimIndent()
        val c = SxcuParser.parse(json).getOrThrow()
        assertEquals("My Host", c.name)
        assertEquals("https://up.example.com/api/upload", c.url)
        assertEquals("POST", c.method)
        assertEquals("Bearer abc123", c.headers["Authorization"])
        assertEquals("image", c.fileFormName)
        assertEquals("data.url", c.responseUrlJsonPath)
    }

    @Test fun supportsBraceJsonSyntaxAndEmbeddedToken() {
        val c = SxcuParser.parse("""{"RequestURL":"https://x/u","URL":"https://x/{json:link}"}""").getOrThrow()
        assertEquals("link", c.responseUrlJsonPath)
    }

    @Test fun appliesDefaultsForMissingFields() {
        val c = SxcuParser.parse("""{"RequestURL":"https://x/u"}""").getOrThrow()
        assertEquals("POST", c.method)
        assertEquals("file", c.fileFormName)
        assertEquals("url", c.responseUrlJsonPath)
        assertEquals(emptyMap<String, String>(), c.headers)
    }

    @Test fun failsOnGarbage() {
        assert(SxcuParser.parse("not json").isFailure)
    }
}

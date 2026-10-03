package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SxcuPresetsTest {
    private fun values(id: String) = SxcuPresets.all.first { it.id == id }.fields.associate { it.key to "v-${it.key}" }

    @Test fun everyPresetRendersAndParses() {
        SxcuPresets.all.forEach { preset ->
            assertTrue(preset.id, SxcuPresets.render(preset.id, values(preset.id)).isSuccess)
        }
    }

    @Test fun xbackboneTrimsHostSlashAndKeepsToken() {
        val spec = SxcuPresets.render("xbackbone", mapOf("host" to "https://x.test/ ", "token" to "t\"1")).getOrThrow()
        assertEquals("https://x.test/upload", spec.requestURL)
        assertEquals("t\"1", spec.arguments["token"])
    }

    @Test fun xbackboneAddsHttpsSchemeWhenMissing() {
        val spec = SxcuPresets.render("xbackbone", mapOf("host" to "x.test", "token" to "t")).getOrThrow()
        assertEquals("https://x.test/upload", spec.requestURL)
    }

    @Test fun bitlyEvaluates() {
        val spec = SxcuPresets.render("bitly", mapOf("token" to "tok")).getOrThrow()
        assertEquals("Bearer tok", spec.headers["Authorization"])
        assertTrue(CustomDestinationType.URL_SHORTENER in spec.destinationTypes)
        val ctx = SyntaxContext(input = "https://long.test", response = SyntaxResponse("""{"link":"https://bit.ly/1"}""", ""))
        assertEquals("""{"long_url":"https://long.test"}""", ShareXSyntax.evaluate(spec.data, ctx))
        assertEquals("https://bit.ly/1", ShareXSyntax.evaluate(spec.url, ctx))
    }

    @Test(expected = SyntaxEvaluationException::class)
    fun pastebinErrorBodyIsNotAUrl() {
        val spec = SxcuPresets.render("pastebin", mapOf("api_key" to "k")).getOrThrow()
        ShareXSyntax.evaluate(spec.url, SyntaxContext(response = SyntaxResponse("Bad API request, invalid api_dev_key", "")))
    }
}

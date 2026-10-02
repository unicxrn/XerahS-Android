package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareXSyntaxResponseTest {
    private fun ctx(body: String, headers: Map<String, List<String>> = emptyMap()) = SyntaxContext(
        response = SyntaxResponse(body = body, url = "https://api.test/upload", headers = headers)
    )
    private val json = """{"data":{"link":"https://i.test/a.png","id":"a"},"files":[{"url":"https://f.test/1"}],"ok":true}"""

    @Test fun responseAndResponseUrl() {
        assertEquals("raw", ShareXSyntax.evaluate("{response}", ctx("raw")))
        assertEquals("https://api.test/upload", ShareXSyntax.evaluate("{responseurl}", ctx("raw")))
    }

    @Test fun headerLookupIsCaseInsensitive() =
        assertEquals("https://l.test", ShareXSyntax.evaluate("{header:location}",
            ctx("", mapOf("Location" to listOf("https://l.test")))))

    @Test fun jsonDotPath() = assertEquals("https://i.test/a.png", ShareXSyntax.evaluate("{json:data.link}", ctx(json)))
    @Test fun jsonArrayIndex() = assertEquals("https://f.test/1", ShareXSyntax.evaluate("{json:files[0].url}", ctx(json)))
    @Test fun jsonDollarPrefix() = assertEquals("a", ShareXSyntax.evaluate("{json:$.data.id}", ctx(json)))
    @Test fun jsonBoolean() = assertEquals("true", ShareXSyntax.evaluate("{json:ok}", ctx(json)))
    @Test fun jsonTemplateComposition() =
        assertEquals("https://cdn.test/a.png", ShareXSyntax.evaluate("https://cdn.test/{json:data.id}.png", ctx(json)))
    @Test fun jsonTwoArgFormUsesFirstArgAsInput() =
        assertEquals("a", ShareXSyntax.evaluate("{json:{response}|data.id}", ctx(json)))

    @Test(expected = SyntaxEvaluationException::class)
    fun jsonMissingPathFails() { ShareXSyntax.evaluate("{json:data.nope}", ctx(json)) }

    @Test fun xpath() = assertEquals("https://x.test/1",
        ShareXSyntax.evaluate("{xml:/rsp/url}", ctx("<rsp><url>https://x.test/1</url></rsp>")))

    @Test fun regexWholeMatchNumberedAndNamedGroups() {
        val c = ctx("""Uploaded: https://r.test/abc ok""")
        assertEquals("https://r.test/abc", ShareXSyntax.evaluate("""{regex:https://\S+}""", c))
        assertEquals("abc", ShareXSyntax.evaluate("""{regex:https://r\.test/(\w+)|1}""", c))
        assertEquals("abc", ShareXSyntax.evaluate("""{regex:https://r\.test/(?<id>\w+)|id}""", c))
    }

    @Test(expected = SyntaxEvaluationException::class)
    fun regexNoMatchFails() { ShareXSyntax.evaluate("{regex:zzz}", ctx("abc")) }

    @Test(expected = SyntaxEvaluationException::class)
    fun regexInvalidPatternFails() { ShareXSyntax.evaluate("""{regex:(abc}""", ctx("id=abc")) }

    @Test(expected = SyntaxEvaluationException::class)
    fun regexGroupIndexOutOfRangeFails() {
        ShareXSyntax.evaluate("""{regex:id=(\w+)|5}""", ctx("id=abc"))
    }

    @Test(expected = SyntaxEvaluationException::class)
    fun regexUnknownNamedGroupFails() {
        ShareXSyntax.evaluate("""{regex:(?<id>\w+)|nope}""", ctx("id=abc"))
    }
}

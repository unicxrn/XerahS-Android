package com.xerahs.android.core.common.sxcu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShareXSyntaxInputTest {
    private val ctx = SyntaxContext(fileName = "shot.png", input = "hello", random = Random(7))

    @Test fun plainTextPassesThrough() =
        assertEquals("https://x.test/upload", ShareXSyntax.evaluate("https://x.test/upload", ctx))

    @Test fun filenameAndInput() =
        assertEquals("shot.png:hello", ShareXSyntax.evaluate("{filename}:{input}", ctx))

    @Test fun functionNamesAreCaseInsensitive() =
        assertEquals("shot.png", ShareXSyntax.evaluate("{FileName}", ctx))

    @Test fun escapedBracesAndPipesAreLiteral() =
        assertEquals("{a|b}", ShareXSyntax.evaluate("""\{a\|b\}""", ctx))

    @Test fun backslashBeforeOrdinaryCharIsKept() =
        assertEquals("""\d+""", ShareXSyntax.evaluate("""\d+""", ctx))

    @Test fun randomPicksOneOption() =
        assertTrue(ShareXSyntax.evaluate("{random:a|b|c}", ctx) in setOf("a", "b", "c"))

    @Test fun selectIsNonInteractiveFirstOption() =
        assertEquals("x", ShareXSyntax.evaluate("{select:x|y}", ctx))

    @Test fun inputboxUsesSuppliedValueElseDefault() {
        val withValue = ctx.copy(inputValues = mapOf("Token" to "abc"))
        assertEquals("abc", ShareXSyntax.evaluate("{inputbox:Token|def}", withValue))
        assertEquals("def", ShareXSyntax.evaluate("{inputbox:Token|def}", ctx))
    }

    @Test fun base64KeepsColonsInArgument() =
        assertEquals("dXNlcjpwYXNz", ShareXSyntax.evaluate("{base64:user:pass}", ctx))

    @Test fun nestedCallsEvaluateInsideOut() =
        assertEquals("c2hvdC5wbmc=", ShareXSyntax.evaluate("{base64:{filename}}", ctx))

    @Test fun unknownFunctionBracesStayLiteral() =
        assertEquals("{nope}", ShareXSyntax.evaluate("{nope}", ctx))

    @Test fun rawJsonBodyKeepsBracesAndEvaluatesInnerCalls() =
        assertEquals("""{"url":"hello"}""", ShareXSyntax.evaluate("""{"url":"{input}"}""", ctx))

    @Test fun unbalancedBraceStaysLiteral() =
        assertEquals("{filename", ShareXSyntax.evaluate("{filename", ctx))

    @Test(expected = SyntaxEvaluationException::class)
    fun responseFunctionWithoutResponseFails() { ShareXSyntax.evaluate("{response}", ctx) }

    @Test fun encoderAppliesOnlyToTopLevelResults() {
        val jsonCtx = ctx.copy(input = "say \"hi\"", resultEncoder = { it.replace("\"", "\\\"") })
        assertEquals("""{"text":"say \"hi\""}""",
            ShareXSyntax.evaluate("""\{"text":"{input}"\}""", jsonCtx))
    }

    @Test fun collectsInputPromptsFromTemplates() {
        val prompts = ShareXSyntax.inputPrompts(
            listOf("https://x.test/{inputbox:Album|main}", "Bearer {inputbox:Token}", "{inputbox:Token}")
        )
        assertEquals(listOf(InputPrompt("Album", "main"), InputPrompt("Token", "")), prompts)
    }
}

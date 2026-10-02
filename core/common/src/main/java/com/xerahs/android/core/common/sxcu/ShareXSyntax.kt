package com.xerahs.android.core.common.sxcu

import java.util.Base64
import kotlin.random.Random

class SyntaxEvaluationException(val expression: String, reason: String) :
    Exception("Couldn't evaluate {$expression}: $reason")

data class SyntaxResponse(
    val body: String,
    val url: String,
    val headers: Map<String, List<String>> = emptyMap(),
)

data class InputPrompt(val title: String, val default: String)

data class SyntaxContext(
    val fileName: String = "",
    /** Text body for text uploaders, or the long URL for URL shorteners. */
    val input: String = "",
    /** Values for {inputbox:Title}, keyed by title. */
    val inputValues: Map<String, String> = emptyMap(),
    val response: SyntaxResponse? = null,
    val random: Random = Random.Default,
    /** Applied to each top-level function result (e.g. JSON-escaping inside a JSON body). */
    val resultEncoder: (String) -> String = { it },
    internal val promptCollector: MutableList<InputPrompt>? = null,
)

/**
 * ShareX custom-uploader syntax: `{name}` / `{name:arg1|arg2}`, nestable, with `\` escaping
 * `{ } | \`. A backslash before any other character is kept literally (so regex `\d` survives).
 * Braces that don't start a known function (e.g. a raw JSON body) or never close are kept literally.
 */
object ShareXSyntax {
    private val ESCAPABLE = setOf('{', '}', '|', '\\')
    private val FUNCTIONS = setOf(
        "filename", "input", "random", "select", "inputbox", "base64",
        "response", "responseurl", "header", "json", "xml", "regex",
    )

    fun evaluate(template: String, ctx: SyntaxContext): String = render(template, ctx, topLevel = true)

    /** Lists every {inputbox} prompt in [templates] (deduplicated by title, first wins). */
    fun inputPrompts(templates: List<String>): List<InputPrompt> {
        val collected = mutableListOf<InputPrompt>()
        val ctx = SyntaxContext(promptCollector = collected)
        templates.forEach { runCatching { evaluate(it, ctx) } }
        return collected.distinctBy { it.title }
    }

    private fun render(template: String, ctx: SyntaxContext, topLevel: Boolean): String {
        val out = StringBuilder()
        var i = 0
        while (i < template.length) {
            val c = template[i]
            when {
                c == '\\' && i + 1 < template.length && template[i + 1] in ESCAPABLE -> {
                    out.append(template[i + 1]); i += 2
                }
                c == '{' -> {
                    val end = findClosing(template, i)
                    val inner = end?.let { template.substring(i + 1, it) }
                    if (inner == null || functionName(inner) !in FUNCTIONS) {
                        out.append(c); i++ // literal brace; keep scanning inside it
                    } else {
                        val result = call(inner, ctx)
                        out.append(if (topLevel) ctx.resultEncoder(result) else result)
                        i = end + 1
                    }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private fun functionName(inner: String): String =
        (if (inner.indexOf(':') < 0) inner else inner.substring(0, inner.indexOf(':'))).trim().lowercase()

    private fun findClosing(s: String, open: Int): Int? {
        var depth = 0
        var i = open
        while (i < s.length) {
            when (s[i]) {
                '\\' -> if (i + 1 < s.length && s[i + 1] in ESCAPABLE) i++
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return null
    }

    /** Splits on top-level unescaped '|', leaving escapes and nested calls untouched. */
    private fun splitArgs(raw: String): List<String> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length && raw[i + 1] in ESCAPABLE) {
                cur.append(c).append(raw[i + 1]); i += 2; continue
            }
            if (c == '{') depth++
            if (c == '}') depth--
            if (c == '|' && depth == 0) {
                parts.add(cur.toString()); cur.clear()
            } else {
                cur.append(c)
            }
            i++
        }
        parts.add(cur.toString())
        return parts
    }

    private fun call(inner: String, ctx: SyntaxContext): String {
        val colon = inner.indexOf(':')
        val name = functionName(inner)
        val args = if (colon < 0) emptyList()
        else splitArgs(inner.substring(colon + 1)).map { render(it, ctx, topLevel = false) }

        return when (name) {
            "filename" -> ctx.fileName
            "input" -> ctx.input
            "random" -> args.ifEmpty { fail(inner, "needs at least one option") }.random(ctx.random)
            "select" -> args.firstOrNull() ?: fail(inner, "needs at least one option")
            "inputbox" -> {
                val title = args.getOrElse(0) { "" }
                val default = args.getOrElse(1) { "" }
                ctx.promptCollector?.add(InputPrompt(title, default))
                ctx.inputValues[title] ?: default
            }
            "base64" -> Base64.getEncoder()
                .encodeToString(args.getOrElse(0) { "" }.toByteArray(Charsets.UTF_8))
            "response" -> response(inner, ctx).body
            "responseurl" -> response(inner, ctx).url
            "header" -> {
                val key = args.getOrElse(0) { fail(inner, "needs a header name") }
                response(inner, ctx).headers.entries
                    .firstOrNull { it.key.equals(key, ignoreCase = true) }
                    ?.value?.firstOrNull() ?: fail(inner, "header not present in response")
            }
            "json" -> {
                val (input, path) = inputAndPath(inner, args, ctx)
                JsonPath.select(input, path) ?: fail(inner, "path not found in response")
            }
            "xml" -> {
                val (input, path) = inputAndPath(inner, args, ctx)
                XmlPath.select(input, path) ?: fail(inner, "XPath matched nothing in response")
            }
            "regex" -> {
                val pattern = args.getOrElse(0) { fail(inner, "needs a pattern") }
                RegexSelect.select(response(inner, ctx).body, pattern, args.getOrNull(1))
                    ?: fail(inner, "no match in response")
            }
            else -> fail(inner, "unknown function '$name'")
        }
    }

    private fun inputAndPath(expr: String, args: List<String>, ctx: SyntaxContext): Pair<String, String> =
        when (args.size) {
            0 -> fail(expr, "needs a path")
            1 -> response(expr, ctx).body to args[0]
            else -> args[0] to args[1]
        }

    private fun response(expr: String, ctx: SyntaxContext): SyntaxResponse =
        ctx.response ?: fail(expr, "only available after the upload response")

    private fun fail(expr: String, reason: String): Nothing = throw SyntaxEvaluationException(expr, reason)
}

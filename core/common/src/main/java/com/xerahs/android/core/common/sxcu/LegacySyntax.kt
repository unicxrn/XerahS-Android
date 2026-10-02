package com.xerahs.android.core.common.sxcu

/** Converts pre-13 ShareX `$name:arg$` syntax to `{name:arg}`. */
internal object LegacySyntax {
    private val TOKEN = Regex(
        """\$(json|xml|regex|responseurl|response|header|filename|random|select|inputbox|base64)(?::([^$]*))?\$""",
        RegexOption.IGNORE_CASE
    )

    fun convert(template: String, regexList: List<String>): String = TOKEN.replace(template) { m ->
        val name = m.groupValues[1].lowercase()
        val arg = m.groupValues[2]
        when {
            name == "regex" -> {
                val parts = arg.split(',')
                val index = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val group = parts.getOrNull(1)?.trim().orEmpty()
                val pattern = index?.let { regexList.getOrNull(it - 1) } ?: return@replace m.value
                "{regex:" + escape(pattern) + (if (group.isNotEmpty()) "|$group" else "") + "}"
            }
            arg.isEmpty() -> "{$name}"
            else -> "{$name:$arg}"
        }
    }

    private fun escape(s: String) = buildString {
        s.forEach { c -> if (c == '{' || c == '}' || c == '|') append('\\'); append(c) }
    }
}

package com.xerahs.android.core.common.net

/** Masks secrets in OkHttp log lines: sensitive header values and query parameter values. */
object LogRedactor {
    private val SENSITIVE = Regex("(?i)(auth|token|secret|passw|apikey|api[-_]?key|x-api|cookie|session|signature|credential)")
    private val QUERY_PARAM = Regex("""([?&])([^=&\s]+)=([^&\s]*)""")
    private const val MASK = "██"

    fun redact(line: String): String {
        val colon = line.indexOf(": ")
        if (colon > 0 && !line.startsWith("-->") && !line.startsWith("<--") &&
            SENSITIVE.containsMatchIn(line.substring(0, colon))
        ) return line.substring(0, colon) + ": " + MASK
        return QUERY_PARAM.replace(line) { m ->
            if (SENSITIVE.containsMatchIn(m.groupValues[2])) "${m.groupValues[1]}${m.groupValues[2]}=$MASK"
            else m.value
        }
    }
}

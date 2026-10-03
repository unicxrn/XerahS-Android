package com.xerahs.android.core.common.sxcu

/** Converts the pre-0.5 single "Custom HTTP" config into a [CustomUploaderSpec]. */
object LegacyCustomHttp {
    fun toSpec(
        url: String,
        method: String,
        headers: Map<String, String>,
        responseUrlJsonPath: String,
        formFieldName: String,
    ) = CustomUploaderSpec(
        name = "Custom HTTP",
        destinationTypes = setOf(CustomDestinationType.IMAGE, CustomDestinationType.FILE),
        requestMethod = method.ifBlank { "POST" }.uppercase(),
        requestURL = url,
        headers = headers,
        body = CustomBodyType.MULTIPART_FORM_DATA,
        fileFormName = formFieldName.ifBlank { "file" },
        url = if (responseUrlJsonPath.isBlank()) "" else "{json:${responseUrlJsonPath.trim()}}",
    )

    /** Old storage format: `key=value` per line. */
    fun parseHeaderLines(text: String): Map<String, String> = text.lines()
        .filter { it.contains('=') }
        .associate { line -> line.split('=', limit = 2).let { (k, v) -> k.trim() to v } }
}

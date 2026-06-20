package com.xerahs.android.core.common.sxcu

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Neutral representation of a ShareX .sxcu custom uploader (no UploadConfig dependency). */
data class SxcuConfig(
    val name: String,
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val fileFormName: String,
    val responseUrlJsonPath: String,
)

object SxcuParser {
    private val JSON_TOKEN = Regex("""[$\{]json:([^$}]+)[$}]""")

    fun parse(text: String): Result<SxcuConfig> = try {
        val root = JsonParser.parseString(text).asJsonObject
        Result.success(
            SxcuConfig(
                name = root.str("Name") ?: "Imported uploader",
                url = root.str("RequestURL") ?: root.str("RequestUrl") ?: "",
                method = root.str("RequestMethod") ?: "POST",
                headers = root.obj("Headers")?.entrySet()
                    ?.associate { (k, v) -> k to v.asString } ?: emptyMap(),
                fileFormName = root.str("FileFormName") ?: "file",
                responseUrlJsonPath = root.str("URL")
                    ?.let { JSON_TOKEN.find(it)?.groupValues?.get(1)?.trim() } ?: "url",
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject
}

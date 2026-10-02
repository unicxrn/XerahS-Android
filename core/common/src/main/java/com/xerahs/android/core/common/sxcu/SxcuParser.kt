package com.xerahs.android.core.common.sxcu

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.net.URI

class SxcuFormatException(message: String) : Exception(message)

/** Parses ShareX .sxcu JSON (any version, case-insensitive keys) into a [CustomUploaderSpec]. */
object SxcuParser {
    fun parse(text: String): Result<CustomUploaderSpec> = runCatching {
        val element = try {
            JsonParser.parseString(text.trim().removePrefix("﻿"))
        } catch (e: Exception) {
            throw SxcuFormatException("Not valid JSON")
        }
        if (!element.isJsonObject) throw SxcuFormatException("Not a ShareX custom uploader")
        val root = element.asJsonObject

        fun field(key: String): JsonElement? = root.entrySet()
            .firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.takeIf { !it.isJsonNull }
        fun str(key: String): String? = field(key)?.takeIf { it.isJsonPrimitive }?.asString
        fun map(key: String): Map<String, String> = field(key)?.takeIf { it.isJsonObject }?.asJsonObject
            ?.entrySet()?.associate { (k, v) -> k to (if (v.isJsonPrimitive) v.asString else v.toString()) }
            .orEmpty()

        val regexList = field("RegexList")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }.orEmpty()
        fun tpl(value: String) = LegacySyntax.convert(value, regexList)

        val requestUrl = str("RequestURL") ?: throw SxcuFormatException("Missing RequestURL")
        val method = (str("RequestMethod") ?: str("RequestType") ?: "POST").trim().uppercase()
        val fileFormName = str("FileFormName").orEmpty()
        val arguments = map("Arguments")
        val body = str("Body")?.let {
            CustomBodyType.fromSxcu(it) ?: throw SxcuFormatException("Unsupported body type: $it")
        } ?: when {
            fileFormName.isNotEmpty() -> CustomBodyType.MULTIPART_FORM_DATA
            arguments.isNotEmpty() && method != "GET" -> CustomBodyType.FORM_URL_ENCODED
            else -> CustomBodyType.NONE
        }
        val types = str("DestinationType")?.split(',')
            ?.mapNotNull { CustomDestinationType.fromSxcu(it) }?.toSet().orEmpty()

        CustomUploaderSpec(
            name = str("Name")?.takeIf { it.isNotBlank() } ?: hostOf(requestUrl) ?: "Custom uploader",
            destinationTypes = types.ifEmpty { setOf(CustomDestinationType.IMAGE, CustomDestinationType.FILE) },
            requestMethod = method,
            requestURL = tpl(requestUrl),
            parameters = map("Parameters").mapValues { tpl(it.value) },
            headers = map("Headers").mapValues { tpl(it.value) },
            body = body,
            arguments = arguments.mapValues { tpl(it.value) },
            fileFormName = fileFormName,
            data = str("Data")?.let(::tpl).orEmpty(),
            url = str("URL")?.let(::tpl).orEmpty(),
            thumbnailURL = str("ThumbnailURL")?.let(::tpl).orEmpty(),
            deletionURL = str("DeletionURL")?.let(::tpl).orEmpty(),
            errorMessage = str("ErrorMessage")?.let(::tpl).orEmpty(),
        )
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
}

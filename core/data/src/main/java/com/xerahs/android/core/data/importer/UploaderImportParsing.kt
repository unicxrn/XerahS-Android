package com.xerahs.android.core.data.importer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.common.sxcu.SxcuParser
import com.xerahs.android.core.domain.model.INSECURE_HTTP_WARNING
import com.xerahs.android.core.domain.model.ImportDraft
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploaderFileKind
import com.xerahs.android.core.domain.model.XsdcImportResult

object UploaderImportParsing {
    fun detect(bytes: ByteArray, fileName: String?): UploaderFileKind {
        when (fileName?.substringAfterLast('.', "")?.lowercase()) {
            "sxcu" -> return UploaderFileKind.SXCU
            "xsdc" -> return UploaderFileKind.XSDC
        }
        val obj = runCatching {
            JsonParser.parseString(String(bytes, Charsets.UTF_8).trim().removePrefix("﻿")).asJsonObject
        }.getOrNull() ?: return UploaderFileKind.UNKNOWN
        return when {
            obj.get("Format")?.takeIf { it.isJsonPrimitive }?.asString == "XerahS.DestinationConfig" -> UploaderFileKind.XSDC
            obj.keySet().any { it.equals("RequestURL", ignoreCase = true) } -> UploaderFileKind.SXCU
            else -> UploaderFileKind.UNKNOWN
        }
    }

    fun sxcuDraft(bytes: ByteArray): Result<ImportDraft> =
        SxcuParser.parse(String(bytes, Charsets.UTF_8)).map { spec ->
            ImportDraft(
                name = spec.name,
                destination = UploadDestination.CUSTOM_HTTP,
                config = UploadConfig.CustomUploaderConfig(spec),
                warnings = warningsFor(spec),
            )
        }

    fun warningsFor(spec: CustomUploaderSpec): List<String> = buildList {
        if (spec.requestURL.trim().startsWith("http://", ignoreCase = true)) add(INSECURE_HTTP_WARNING)
    }

    fun xsdcDrafts(destinations: List<XsdcDestination>): XsdcImportResult {
        val drafts = mutableListOf<ImportDraft>()
        val skipped = mutableListOf<String>()
        destinations.forEach { d ->
            val label = d.displayName.ifBlank { d.providerId }
            when {
                !d.providerId.equals("amazons3", ignoreCase = true) ->
                    skipped.add("$label (${d.providerId} isn't supported on Android yet)")
                !d.config.str("AuthMode").equals("AccessKeys", ignoreCase = true) ->
                    skipped.add("$label (only access-key S3 auth is supported)")
                else -> drafts.add(
                    ImportDraft(
                        name = d.displayName.ifBlank { "Amazon S3" },
                        destination = UploadDestination.S3,
                        config = s3Config(d.config),
                        makeDefault = d.isDefault,
                    )
                )
            }
        }
        return XsdcImportResult(drafts, skipped)
    }

    private fun s3Config(c: JsonObject) = UploadConfig.S3Config(
        accessKeyId = c.str("AccessKeyId").orEmpty(),
        secretAccessKey = c.str("SecretAccessKey").orEmpty(),
        region = c.str("Region")?.ifBlank { null } ?: "us-east-1",
        bucket = c.str("BucketName").orEmpty(),
        endpoint = c.str("Endpoint")?.ifBlank { null }?.let(::withScheme),
        customUrl = c.str("CustomDomain")?.ifBlank { null }?.takeIf { c.bool("UseCustomDomain") }?.let(::withScheme),
        acl = if (c.bool("SetPublicAcl")) "public-read" else "",
        usePathStyle = c.bool("UsePathStyle"),
    )

    /**
     * Upstream may export a bare host (e.g. "minio.example.com:9000") for a non-AWS
     * endpoint or custom domain. S3Uploader needs a URI with a scheme, so default to https.
     */
    private fun withScheme(value: String): String =
        if (value.contains("://")) value else "https://$value"

    private fun JsonObject.str(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.bool(k: String): Boolean = get(k)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
}

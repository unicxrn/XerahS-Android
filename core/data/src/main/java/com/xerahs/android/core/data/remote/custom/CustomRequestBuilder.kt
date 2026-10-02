package com.xerahs.android.core.data.remote.custom

import com.google.gson.JsonPrimitive
import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.common.sxcu.CustomBodyType
import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.common.sxcu.ShareXSyntax
import com.xerahs.android.core.common.sxcu.SyntaxContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

internal object CustomRequestBuilder {
    private val BODY_REQUIRED = setOf("POST", "PUT", "PATCH")

    fun build(spec: CustomUploaderSpec, file: File?, fileName: String, ctx: SyntaxContext): Request {
        fun eval(t: String) = ShareXSyntax.evaluate(t, ctx)

        val url = eval(spec.requestURL).trim().toHttpUrl().newBuilder().apply {
            spec.parameters.forEach { (k, v) -> addQueryParameter(k, eval(v)) }
        }.build()
        val builder = Request.Builder().url(url)
        spec.headers.forEach { (k, v) -> builder.header(k, eval(v)) }

        val args = spec.arguments.mapValues { eval(it.value) }
        val fileType = MimeTypes.fromFileName(fileName).toMediaType()
        val body: RequestBody? = when (spec.body) {
            CustomBodyType.NONE -> null
            CustomBodyType.MULTIPART_FORM_DATA -> {
                val attachFile = file != null && spec.fileFormName.isNotBlank()
                if (args.isEmpty() && !attachFile) null
                else MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                    args.forEach { (k, v) -> addFormDataPart(k, v) }
                    if (attachFile) addFormDataPart(spec.fileFormName, fileName, file!!.asRequestBody(fileType))
                }.build()
            }
            CustomBodyType.FORM_URL_ENCODED ->
                FormBody.Builder().apply { args.forEach { (k, v) -> add(k, v) } }.build()
            CustomBodyType.JSON -> ShareXSyntax
                .evaluate(spec.data, ctx.copy(resultEncoder = ::jsonEscape))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            CustomBodyType.XML -> ShareXSyntax
                .evaluate(spec.data, ctx.copy(resultEncoder = ::xmlEscape))
                .toRequestBody("application/xml; charset=utf-8".toMediaType())
            CustomBodyType.BINARY ->
                (file ?: throw IllegalArgumentException("Binary body needs a file")).asRequestBody(fileType)
        }

        val method = spec.requestMethod.trim().uppercase()
        builder.method(method, body ?: if (method in BODY_REQUIRED) ByteArray(0).toRequestBody(null) else null)
        return builder.build()
    }

    private fun jsonEscape(s: String): String = JsonPrimitive(s).toString().let { it.substring(1, it.length - 1) }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}

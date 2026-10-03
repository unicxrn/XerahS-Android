package com.xerahs.android.core.data.remote.custom

import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.common.sxcu.ShareXSyntax
import com.xerahs.android.core.common.sxcu.SyntaxContext
import com.xerahs.android.core.common.sxcu.SyntaxResponse

internal object CustomResponseParser {
    fun parse(spec: CustomUploaderSpec, code: Int, response: SyntaxResponse, ctx: SyntaxContext): CustomUploadOutcome {
        val rctx = ctx.copy(response = response)
        if (code !in 200..299) {
            val templated = spec.errorMessage.takeIf { it.isNotBlank() }
                ?.let { runCatching { ShareXSyntax.evaluate(it, rctx) }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
            return CustomUploadOutcome.Failure(templated ?: "HTTP $code: ${response.body.take(300)}")
        }
        val url = ShareXSyntax.evaluate(spec.url.ifBlank { "{response}" }, rctx).trim()
        if (url.isEmpty()) return CustomUploadOutcome.Failure("The uploader returned an empty URL")
        return CustomUploadOutcome.Success(
            url = url,
            thumbnailUrl = optional(spec.thumbnailURL, rctx),
            deletionUrl = optional(spec.deletionURL, rctx),
        )
    }

    private fun optional(template: String, ctx: SyntaxContext): String? =
        template.takeIf { it.isNotBlank() }
            ?.let { runCatching { ShareXSyntax.evaluate(it, ctx).trim() }.getOrNull() }
            ?.ifBlank { null }
}

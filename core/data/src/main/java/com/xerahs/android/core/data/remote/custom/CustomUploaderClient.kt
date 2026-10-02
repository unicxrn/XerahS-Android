package com.xerahs.android.core.data.remote.custom

import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.common.sxcu.SyntaxContext
import com.xerahs.android.core.common.sxcu.SyntaxEvaluationException
import com.xerahs.android.core.common.sxcu.SyntaxResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class CustomUploadInput(
    val file: File?,
    val fileName: String,
    /** Text content (text uploaders) or long URL (URL shorteners); exposed as {input}. */
    val input: String = "",
    /** {inputbox:Title} answers keyed by title. */
    val inputValues: Map<String, String> = emptyMap(),
)

sealed interface CustomUploadOutcome {
    data class Success(val url: String, val thumbnailUrl: String?, val deletionUrl: String?) : CustomUploadOutcome
    data class Failure(val message: String) : CustomUploadOutcome
}

/** Executes a ShareX-style custom uploader request. Never throws; failures are [CustomUploadOutcome.Failure]. */
@Singleton
class CustomUploaderClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun execute(spec: CustomUploaderSpec, input: CustomUploadInput): CustomUploadOutcome =
        withContext(Dispatchers.IO) {
            try {
                val ctx = SyntaxContext(fileName = input.fileName, input = input.input, inputValues = input.inputValues)
                val request = CustomRequestBuilder.build(spec, input.file, input.fileName, ctx)
                okHttpClient.newCall(request).execute().use { resp ->
                    val response = SyntaxResponse(
                        body = resp.body?.string().orEmpty(),
                        url = resp.request.url.toString(),
                        headers = resp.headers.toMultimap(),
                    )
                    CustomResponseParser.parse(spec, resp.code, response, ctx)
                }
            } catch (e: SyntaxEvaluationException) {
                CustomUploadOutcome.Failure(e.message ?: "Custom uploader syntax error")
            } catch (e: IOException) {
                CustomUploadOutcome.Failure("Network error: ${e.message}")
            } catch (e: IllegalArgumentException) {
                CustomUploadOutcome.Failure("Invalid request: ${e.message}")
            }
        }
}

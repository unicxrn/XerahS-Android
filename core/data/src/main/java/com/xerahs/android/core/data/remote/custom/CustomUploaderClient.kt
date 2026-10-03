package com.xerahs.android.core.data.remote.custom

import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.common.sxcu.SyntaxContext
import com.xerahs.android.core.common.sxcu.SyntaxEvaluationException
import com.xerahs.android.core.common.sxcu.SyntaxResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

/** Maximum response body read into memory; prevents a huge/unbounded response from OOMing the device. */
private const val MAX_RESPONSE_BODY_BYTES = 1_000_000L

/** Executes a ShareX-style custom uploader request. Never throws; failures are [CustomUploadOutcome.Failure]. */
@Singleton
class CustomUploaderClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun execute(spec: CustomUploaderSpec, input: CustomUploadInput): CustomUploadOutcome {
        return try {
            val ctx = SyntaxContext(fileName = input.fileName, input = input.input, inputValues = input.inputValues)
            val request = CustomRequestBuilder.build(spec, input.file, input.fileName, ctx)
            val call = okHttpClient.newCall(request)
            val resp = executeCancellable(call)
            withContext(Dispatchers.IO) {
                resp.use {
                    val response = SyntaxResponse(
                        body = it.peekBody(MAX_RESPONSE_BODY_BYTES).string(),
                        url = it.request.url.toString(),
                        headers = it.headers.toMultimap(),
                    )
                    CustomResponseParser.parse(spec, it.code, response, ctx)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyntaxEvaluationException) {
            CustomUploadOutcome.Failure(e.message ?: "Custom uploader syntax error")
        } catch (e: IOException) {
            CustomUploadOutcome.Failure("Network error: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: IllegalArgumentException) {
            // Strip anything after the first ':' — OkHttp's header validation messages echo the
            // offending header name/value there, which could leak secrets (e.g. an auth token) into logs/UI.
            val safe = e.message?.substringBefore(':')?.trim()?.ifBlank { null } ?: "invalid request"
            CustomUploadOutcome.Failure("Invalid request: $safe")
        }
    }

    /** Runs [call] on OkHttp's async dispatcher so cancelling the coroutine cancels the in-flight HTTP call too. */
    private suspend fun executeCancellable(call: Call): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }
}

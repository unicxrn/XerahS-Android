package com.xerahs.android.feature.upload.uploader

import androidx.annotation.VisibleForTesting
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GistUploader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    @VisibleForTesting
    internal var apiBase = "https://api.github.com"

    suspend fun upload(file: File, config: UploadConfig.GistConfig, remoteFileName: String): UploadResult = try {
        if (file.length() > MAX_BYTES) {
            failure("Gist files must be under 1 MB")
        } else {
            val text = withContext(Dispatchers.IO) { file.readText() }
            val json = JsonObject().apply {
                addProperty("public", config.isPublic)
                add("files", JsonObject().apply {
                    add(remoteFileName, JsonObject().apply { addProperty("content", text) })
                })
            }
            val reply = okHttpClient.send(
                Request.Builder().url("$apiBase/gists")
                    .header("Authorization", "Bearer ${config.token.trim()}")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            )
            val url = if (reply.ok) runCatching { JsonParser.parseString(reply.body).asJsonObject.get("html_url").asString }.getOrNull() else null
            when {
                !reply.ok -> failure(hostError("GitHub", reply))
                url.isNullOrBlank() -> failure("GitHub returned no gist URL")
                else -> UploadResult(success = true, url = url, destination = UploadDestination.GITHUB_GIST)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        failure("GitHub network error: ${e.message ?: e.javaClass.simpleName}")
    } catch (e: IllegalArgumentException) {
        failure("GitHub request is invalid (check the token)")
    }

    private fun failure(message: String) = UploadResult(success = false, errorMessage = message, destination = UploadDestination.GITHUB_GIST)

    private companion object {
        const val MAX_BYTES = 1_000_000L
    }
}

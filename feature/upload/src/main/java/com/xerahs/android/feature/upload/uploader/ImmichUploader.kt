package com.xerahs.android.feature.upload.uploader

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImmichUploader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun upload(file: File, config: UploadConfig.ImmichConfig, remoteFileName: String): UploadResult = try {
        val base = config.serverUrl.trim().trimEnd('/').removeSuffix("/api")
        val apiKey = config.apiKey.trim()
        val modified = Instant.ofEpochMilli(file.lastModified()).toString()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("deviceAssetId", "$remoteFileName-${file.length()}-${file.lastModified()}")
            .addFormDataPart("deviceId", "xerahs-android")
            .addFormDataPart("fileCreatedAt", modified)
            .addFormDataPart("fileModifiedAt", modified)
            .addFormDataPart("assetData", remoteFileName, file.asRequestBody(MimeTypes.fromFileName(remoteFileName).toMediaType()))
            .build()
        val upload = okHttpClient.send(request("$base/api/assets", apiKey).post(body).build())
        val id = if (upload.ok) runCatching { JsonParser.parseString(upload.body).asJsonObject.get("id").asString }.getOrNull() else null
        when {
            !upload.ok -> failure(hostError("Immich", upload))
            id.isNullOrBlank() -> failure("Immich returned no asset id")
            !config.createShareLink -> success("$base/photos/$id")
            else -> shareLink(base, apiKey, id)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        failure("Immich network error: ${e.message ?: e.javaClass.simpleName}")
    } catch (e: IllegalArgumentException) {
        failure("Immich request is invalid (check the server URL and API key)")
    }

    private suspend fun shareLink(base: String, apiKey: String, assetId: String): UploadResult {
        val json = JsonObject().apply {
            addProperty("type", "INDIVIDUAL")
            add("assetIds", JsonArray().apply { add(assetId) })
        }
        val reply = okHttpClient.send(
            request("$base/api/shared-links", apiKey)
                .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        )
        if (!reply.ok) return failure("Uploaded, but sharing failed: " + hostError("Immich", reply))
        val key = runCatching { JsonParser.parseString(reply.body).asJsonObject.get("key").asString }.getOrNull()
        return if (key.isNullOrBlank()) failure("Uploaded, but Immich returned no share key") else success("$base/share/$key")
    }

    private fun request(url: String, apiKey: String) = Request.Builder().url(url.toHttpUrl())
        .header("x-api-key", apiKey).header("Accept", "application/json")

    private fun success(url: String) = UploadResult(success = true, url = url, destination = UploadDestination.IMMICH)
    private fun failure(message: String) = UploadResult(success = false, errorMessage = message, destination = UploadDestination.IMMICH)
}

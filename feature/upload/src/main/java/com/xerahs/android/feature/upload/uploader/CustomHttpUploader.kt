package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomHttpUploader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun upload(
        file: File,
        config: UploadConfig.CustomHttpConfig,
        remoteFileName: String? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val fileName = remoteFileName ?: file.name
            val contentType = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }

            val requestBody = file.asRequestBody(contentType.toMediaType())
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(config.formFieldName, fileName, requestBody)
                .build()

            val requestBuilder = Request.Builder()
                .url(config.url)

            when (config.method.uppercase()) {
                "PUT" -> requestBuilder.put(multipartBody)
                else -> requestBuilder.post(multipartBody)
            }

            config.headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val url = extractUrl(responseBody, config.responseUrlJsonPath)
                UploadResult(
                    success = true,
                    url = url ?: responseBody.take(500),
                    destination = UploadDestination.CUSTOM_HTTP
                )
            } else {
                UploadResult(
                    success = false,
                    errorMessage = "HTTP upload failed: ${response.code} ${response.message}\n${responseBody.take(500)}",
                    destination = UploadDestination.CUSTOM_HTTP
                )
            }
        } catch (e: Exception) {
            UploadResult(
                success = false,
                errorMessage = "HTTP upload error: ${e.message}",
                destination = UploadDestination.CUSTOM_HTTP
            )
        }
    }

    private fun extractUrl(json: String, jsonPath: String): String? {
        return try {
            val obj = JSONObject(json)
            val parts = jsonPath.split(".")
            var current: Any = obj
            for (part in parts) {
                current = when (current) {
                    is JSONObject -> current.get(part)
                    else -> return null
                }
            }
            current.toString()
        } catch (e: Exception) {
            null
        }
    }
}

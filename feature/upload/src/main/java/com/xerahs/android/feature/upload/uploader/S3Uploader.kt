package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.common.AwsV4Signer
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class S3Uploader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun upload(file: File, config: UploadConfig.S3Config, remoteFileName: String? = null): UploadResult =
        withContext(Dispatchers.IO) {
            try {
                val fileName = remoteFileName ?: file.name
                val objectKey = if (config.prefix.isNotEmpty()) {
                    "${config.prefix.trimEnd('/')}/$fileName"
                } else {
                    fileName
                }

                val host: String
                val url: String

                val configEndpoint = config.endpoint
                if (configEndpoint != null && configEndpoint.isNotEmpty()) {
                    // Custom endpoint (MinIO, DigitalOcean Spaces, etc.)
                    val endpoint = configEndpoint.trimEnd('/')
                    if (config.usePathStyle) {
                        host = URI(endpoint).host
                        url = "$endpoint/${config.bucket}/$objectKey"
                    } else {
                        host = "${config.bucket}.${URI(endpoint).host}"
                        url = "${endpoint.replace(URI(endpoint).host, host)}/$objectKey"
                    }
                } else {
                    // Standard AWS S3
                    // Bucket names with dots break virtual-hosted-style because the
                    // wildcard SSL cert *.s3.region.amazonaws.com only covers one
                    // subdomain level. Use path-style for dotted bucket names.
                    if (config.bucket.contains('.')) {
                        host = "s3.${config.region}.amazonaws.com"
                        url = "https://$host/${config.bucket}/$objectKey"
                    } else {
                        host = "${config.bucket}.s3.${config.region}.amazonaws.com"
                        url = "https://$host/$objectKey"
                    }
                }

                val fileBytes = file.readBytes()
                val contentType = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    else -> "application/octet-stream"
                }

                val headers = mutableMapOf(
                    "Content-Type" to contentType
                )
                if (config.acl.isNotEmpty()) {
                    headers["x-amz-acl"] = config.acl
                }

                val signed = AwsV4Signer.sign(
                    method = "PUT",
                    url = url,
                    headers = headers,
                    payload = fileBytes,
                    accessKeyId = config.accessKeyId,
                    secretAccessKey = config.secretAccessKey,
                    region = config.region,
                    host = host
                )

                val requestBuilder = Request.Builder()
                    .url(url)
                    .put(fileBytes.toRequestBody())

                // Add all signed headers
                signed.headers.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                requestBuilder.addHeader("Authorization", signed.authorization)

                val response = okHttpClient.newCall(requestBuilder.build()).execute()

                if (response.isSuccessful) {
                    val publicUrl = config.customUrl?.let { base ->
                        val baseUrl = base.trimEnd('/')
                        "$baseUrl/$objectKey"
                    } ?: url
                    UploadResult(
                        success = true,
                        url = publicUrl,
                        destination = UploadDestination.S3
                    )
                } else {
                    val errorBody = response.body?.string()?.take(500) ?: ""
                    UploadResult(
                        success = false,
                        errorMessage = "S3 upload failed: ${response.code} ${response.message}\n$errorBody",
                        destination = UploadDestination.S3
                    )
                }
            } catch (e: Exception) {
                UploadResult(
                    success = false,
                    errorMessage = "S3 upload error: ${e.message}",
                    destination = UploadDestination.S3
                )
            }
        }
}

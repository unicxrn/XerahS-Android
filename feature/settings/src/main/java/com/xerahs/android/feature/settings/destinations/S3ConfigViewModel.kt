package com.xerahs.android.feature.settings.destinations

import androidx.lifecycle.ViewModel
import com.xerahs.android.core.common.AwsV4Signer
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class S3ConfigViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    suspend fun loadConfig(): UploadConfig.S3Config =
        settingsRepository.getS3Config()

    suspend fun saveConfig(
        accessKeyId: String, secretAccessKey: String, region: String,
        bucket: String, endpoint: String?, customUrl: String?, prefix: String,
        acl: String, usePathStyle: Boolean
    ) {
        settingsRepository.saveS3Config(
            UploadConfig.S3Config(
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                region = region,
                bucket = bucket,
                endpoint = endpoint,
                customUrl = customUrl,
                prefix = prefix,
                acl = acl,
                usePathStyle = usePathStyle
            )
        )
    }

    suspend fun testConnection(
        accessKeyId: String, secretAccessKey: String, region: String,
        bucket: String, endpoint: String?, usePathStyle: Boolean
    ): String = withContext(Dispatchers.IO) {
        try {
            // Build host and URL using the same logic as S3Uploader
            val host: String
            val url: String

            if (endpoint != null && endpoint.isNotEmpty()) {
                val ep = endpoint.trimEnd('/')
                if (usePathStyle) {
                    host = URI(ep).host
                    url = "$ep/$bucket?list-type=2&max-keys=1"
                } else {
                    host = "$bucket.${URI(ep).host}"
                    url = "${ep.replace(URI(ep).host, host)}?list-type=2&max-keys=1"
                }
            } else {
                if (bucket.contains('.')) {
                    host = "s3.$region.amazonaws.com"
                    url = "https://$host/$bucket?list-type=2&max-keys=1"
                } else {
                    host = "$bucket.s3.$region.amazonaws.com"
                    url = "https://$host?list-type=2&max-keys=1"
                }
            }

            // Sign the request with AWS Signature V4
            val signed = AwsV4Signer.sign(
                method = "GET",
                url = url,
                headers = emptyMap(),
                payload = ByteArray(0),
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                region = region,
                host = host
            )

            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            signed.headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            requestBuilder.addHeader("Authorization", signed.authorization)

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val response = client.newCall(requestBuilder.build()).execute()
            when {
                response.isSuccessful -> "Connection successful! Bucket accessible."
                response.code == 403 -> "Authentication failed: check your access key and secret."
                response.code == 404 -> "Bucket not found: check bucket name and region."
                response.code == 301 -> "Wrong region: the bucket may be in a different region."
                else -> {
                    val body = response.body?.string()?.take(200) ?: ""
                    "Failed: HTTP ${response.code} ${response.message}\n$body"
                }
            }
        } catch (e: java.net.UnknownHostException) {
            "Connection failed: could not resolve host. Check endpoint/region."
        } catch (e: java.net.SocketTimeoutException) {
            "Connection timed out: check endpoint and network."
        } catch (e: Exception) {
            "Connection failed: ${e.message}"
        }
    }
}

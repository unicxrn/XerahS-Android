package com.xerahs.android.feature.s3explorer.data

import com.xerahs.android.core.common.AwsV4Signer
import com.xerahs.android.core.common.Result
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.feature.s3explorer.model.S3Folder
import com.xerahs.android.feature.s3explorer.model.S3ListResult
import com.xerahs.android.feature.s3explorer.model.S3Object
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class S3ApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    private data class HostAndBaseUrl(val host: String, val baseUrl: String)

    private fun resolveHostAndBaseUrl(config: UploadConfig.S3Config): HostAndBaseUrl {
        val configEndpoint = config.endpoint
        return if (configEndpoint != null && configEndpoint.isNotEmpty()) {
            val endpoint = configEndpoint.trimEnd('/')
            if (config.usePathStyle) {
                val host = URI(endpoint).host
                HostAndBaseUrl(host, "$endpoint/${config.bucket}")
            } else {
                val host = "${config.bucket}.${URI(endpoint).host}"
                HostAndBaseUrl(host, "${endpoint.replace(URI(endpoint).host, host)}")
            }
        } else {
            if (config.bucket.contains('.')) {
                val host = "s3.${config.region}.amazonaws.com"
                HostAndBaseUrl(host, "https://$host/${config.bucket}")
            } else {
                val host = "${config.bucket}.s3.${config.region}.amazonaws.com"
                HostAndBaseUrl(host, "https://$host")
            }
        }
    }

    private fun signAndExecute(
        config: UploadConfig.S3Config,
        method: String,
        path: String,
        queryString: String = "",
        payload: ByteArray = ByteArray(0),
        extraHeaders: Map<String, String> = emptyMap(),
        contentType: String? = null
    ): okhttp3.Response {
        val (host, baseUrl) = resolveHostAndBaseUrl(config)
        val encodedPath = path.split("/").joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        val url = if (queryString.isNotEmpty()) {
            "$baseUrl$encodedPath?$queryString"
        } else {
            "$baseUrl$encodedPath"
        }

        val allHeaders = extraHeaders.toMutableMap()
        if (contentType != null) {
            allHeaders["Content-Type"] = contentType
        }

        val signed = AwsV4Signer.sign(
            method = method,
            url = url,
            headers = allHeaders,
            payload = payload,
            accessKeyId = config.accessKeyId,
            secretAccessKey = config.secretAccessKey,
            region = config.region,
            host = host
        )

        val requestBuilder = Request.Builder().url(url)
        when (method) {
            "GET" -> requestBuilder.get()
            "DELETE" -> requestBuilder.delete()
            "PUT" -> {
                val mediaType = contentType?.let { it.toMediaTypeOrNull() }
                requestBuilder.put(payload.toRequestBody(mediaType))
            }
        }

        signed.headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }
        extraHeaders.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }
        requestBuilder.addHeader("Authorization", signed.authorization)

        return okHttpClient.newCall(requestBuilder.build()).execute()
    }

    suspend fun putObject(
        config: UploadConfig.S3Config,
        key: String,
        body: ByteArray,
        contentType: String = "application/octet-stream"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Result.runCatching {
            val response = signAndExecute(
                config, "PUT", "/$key",
                payload = body,
                contentType = contentType
            )
            if (!response.isSuccessful) {
                val responseBody = response.body?.string()?.take(500) ?: ""
                throw Exception("S3 PUT failed: ${response.code} ${response.message}\n$responseBody")
            }
        }
    }

    suspend fun copyObject(
        config: UploadConfig.S3Config,
        sourceKey: String,
        destKey: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Result.runCatching {
            val copySource = "/${config.bucket}/$sourceKey"
            val response = signAndExecute(
                config, "PUT", "/$destKey",
                extraHeaders = mapOf("x-amz-copy-source" to copySource)
            )
            if (!response.isSuccessful) {
                val responseBody = response.body?.string()?.take(500) ?: ""
                throw Exception("S3 copy failed: ${response.code} ${response.message}\n$responseBody")
            }
        }
    }

    suspend fun listObjects(
        config: UploadConfig.S3Config,
        prefix: String = "",
        delimiter: String = "/",
        maxKeys: Int = 1000,
        continuationToken: String? = null
    ): Result<S3ListResult> = withContext(Dispatchers.IO) {
        Result.runCatching {
            val queryParams = buildList {
                add("list-type=2")
                if (prefix.isNotEmpty()) add("prefix=${uriEncode(prefix)}")
                if (delimiter.isNotEmpty()) add("delimiter=${uriEncode(delimiter)}")
                add("max-keys=$maxKeys")
                if (continuationToken != null) add("continuation-token=${uriEncode(continuationToken)}")
            }.joinToString("&")

            val response = signAndExecute(config, "GET", "/", queryParams)
            val body = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                throw Exception("S3 list failed: ${response.code} ${response.message}\n${body.take(500)}")
            }

            parseListObjectsV2Response(body)
        }
    }

    suspend fun deleteObject(
        config: UploadConfig.S3Config,
        objectKey: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Result.runCatching {
            val response = signAndExecute(config, "DELETE", "/$objectKey")
            if (!response.isSuccessful && response.code != 204) {
                val body = response.body?.string()?.take(500) ?: ""
                throw Exception("S3 delete failed: ${response.code} ${response.message}\n$body")
            }
        }
    }

    fun buildSignedUrl(
        config: UploadConfig.S3Config,
        objectKey: String
    ): Pair<String, Map<String, String>> {
        val (host, baseUrl) = resolveHostAndBaseUrl(config)
        val encodedKey = objectKey.split("/").joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        val url = "$baseUrl/$encodedKey"

        val signed = AwsV4Signer.sign(
            method = "GET",
            url = url,
            headers = emptyMap(),
            payload = ByteArray(0),
            accessKeyId = config.accessKeyId,
            secretAccessKey = config.secretAccessKey,
            region = config.region,
            host = host
        )

        val headers = mutableMapOf<String, String>()
        signed.headers.forEach { (key, value) ->
            headers[key] = value
        }
        headers["Authorization"] = signed.authorization

        return Pair(url, headers)
    }

    suspend fun downloadObject(
        config: UploadConfig.S3Config,
        objectKey: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        Result.runCatching {
            val response = signAndExecute(config, "GET", "/$objectKey")
            if (!response.isSuccessful) {
                val body = response.body?.string()?.take(500) ?: ""
                throw Exception("S3 download failed: ${response.code} ${response.message}\n$body")
            }
            response.body?.bytes() ?: throw Exception("Empty response body")
        }
    }

    private fun parseListObjectsV2Response(xml: String): S3ListResult {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val objects = mutableListOf<S3Object>()
        val folders = mutableListOf<S3Folder>()
        var isTruncated = false
        var nextContinuationToken: String? = null

        var currentTag = ""
        var inContents = false
        var inCommonPrefixes = false

        // Contents fields
        var key = ""
        var size = 0L
        var lastModified = ""
        var etag = ""
        var storageClass = ""

        // CommonPrefixes field
        var prefix = ""

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateFormatAlt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "Contents" -> {
                            inContents = true
                            key = ""; size = 0; lastModified = ""; etag = ""; storageClass = ""
                        }
                        "CommonPrefixes" -> {
                            inCommonPrefixes = true
                            prefix = ""
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        when {
                            inContents -> when (currentTag) {
                                "Key" -> key = text
                                "Size" -> size = text.toLongOrNull() ?: 0
                                "LastModified" -> lastModified = text
                                "ETag" -> etag = text.trim('"')
                                "StorageClass" -> storageClass = text
                            }
                            inCommonPrefixes -> when (currentTag) {
                                "Prefix" -> prefix = text
                            }
                            else -> when (currentTag) {
                                "IsTruncated" -> isTruncated = text.equals("true", ignoreCase = true)
                                "NextContinuationToken" -> nextContinuationToken = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "Contents" -> {
                            inContents = false
                            val name = key.substringAfterLast('/')
                            if (name.isNotEmpty()) {
                                val ts = try {
                                    dateFormat.parse(lastModified)?.time ?: 0L
                                } catch (_: Exception) {
                                    try {
                                        dateFormatAlt.parse(lastModified)?.time ?: 0L
                                    } catch (_: Exception) { 0L }
                                }
                                objects.add(
                                    S3Object(
                                        key = key,
                                        name = name,
                                        size = size,
                                        lastModified = ts,
                                        etag = etag,
                                        storageClass = storageClass
                                    )
                                )
                            }
                        }
                        "CommonPrefixes" -> {
                            inCommonPrefixes = false
                            if (prefix.isNotEmpty()) {
                                val folderName = prefix.trimEnd('/').substringAfterLast('/')
                                folders.add(S3Folder(prefix = prefix, name = folderName))
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return S3ListResult(
            objects = objects,
            folders = folders,
            isTruncated = isTruncated,
            nextContinuationToken = nextContinuationToken
        )
    }

    private fun uriEncode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}

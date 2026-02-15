package com.xerahs.android.core.common

import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.TreeMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 signing implementation.
 * Ported from XerahS AwsS3Signer.cs
 */
object AwsV4Signer {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"
    private const val TERMINATOR = "aws4_request"

    data class SignedRequest(
        val authorization: String,
        val date: String,
        val contentSha256: String,
        val headers: Map<String, String>
    )

    fun sign(
        method: String,
        url: String,
        headers: Map<String, String>,
        payload: ByteArray,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        host: String
    ): SignedRequest {
        val now = Date()
        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val shortDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val dateTime = dateFormat.format(now)
        val date = shortDateFormat.format(now)

        // Hash the payload
        val payloadHash = sha256Hex(payload)

        // Build sorted headers map
        val allHeaders = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
        allHeaders.putAll(headers)
        allHeaders["host"] = host
        allHeaders["x-amz-date"] = dateTime
        allHeaders["x-amz-content-sha256"] = payloadHash

        // Parse URL for path and query
        val uri = java.net.URI(url)
        val path = uri.path.ifEmpty { "/" }
        val query = uri.query ?: ""

        // 1. Build canonical request
        val canonicalHeaders = allHeaders.entries.joinToString("") {
            "${it.key.lowercase()}:${it.value.trim()}\n"
        }
        val signedHeaders = allHeaders.keys.joinToString(";") { it.lowercase() }

        val canonicalQueryString = if (query.isEmpty()) "" else {
            query.split("&").map {
                val parts = it.split("=", limit = 2)
                val key = URLEncoder.encode(parts[0], "UTF-8")
                val value = if (parts.size > 1) URLEncoder.encode(parts[1], "UTF-8") else ""
                "$key=$value"
            }.sorted().joinToString("&")
        }

        val canonicalRequest = listOf(
            method,
            uriEncode(path, true),
            canonicalQueryString,
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")

        // 2. Build string to sign
        val scope = "$date/$region/$SERVICE/$TERMINATOR"
        val stringToSign = listOf(
            ALGORITHM,
            dateTime,
            scope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))
        ).joinToString("\n")

        // 3. Derive signing key
        val signingKey = deriveSigningKey(secretAccessKey, date, region)

        // 4. Calculate signature
        val signature = hmacSha256Hex(signingKey, stringToSign)

        // 5. Build authorization header
        val authorization = "$ALGORITHM Credential=$accessKeyId/$scope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"

        return SignedRequest(
            authorization = authorization,
            date = dateTime,
            contentSha256 = payloadHash,
            headers = allHeaders
        )
    }

    private fun deriveSigningKey(secretKey: String, date: String, region: String): ByteArray {
        val kDate = hmacSha256("AWS4$secretKey".toByteArray(Charsets.UTF_8), date)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, SERVICE)
        return hmacSha256(kService, TERMINATOR)
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        return hmacSha256(key, data).toHex()
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun uriEncode(path: String, preserveSlashes: Boolean): String {
        val encoded = StringBuilder()
        for (ch in path) {
            when {
                ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '~' || ch == '.' -> encoded.append(ch)
                ch == '/' && preserveSlashes -> encoded.append(ch)
                else -> {
                    val bytes = ch.toString().toByteArray(Charsets.UTF_8)
                    for (b in bytes) {
                        encoded.append("%%%02X".format(b))
                    }
                }
            }
        }
        return encoded.toString()
    }
}

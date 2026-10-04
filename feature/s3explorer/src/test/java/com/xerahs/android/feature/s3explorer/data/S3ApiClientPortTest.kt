package com.xerahs.android.feature.s3explorer.data

import com.xerahs.android.core.domain.model.UploadConfig
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class S3ApiClientPortTest {

    private val client = S3ApiClient(OkHttpClient())

    @Test fun pathStyleEndpointPreservesNonDefaultPort() {
        val config = UploadConfig.S3Config(
            accessKeyId = "key",
            secretAccessKey = "secret",
            region = "us-east-1",
            bucket = "bucket",
            endpoint = "http://minio:9000",
            usePathStyle = true
        )

        val (url, headers) = client.buildSignedUrl(config, "a/x.png")

        assertEquals("http://minio:9000/bucket/a/x.png", url)
        assertEquals("minio:9000", headers["host"])
    }

    @Test fun virtualHostedEndpointPreservesNonDefaultPort() {
        val config = UploadConfig.S3Config(
            accessKeyId = "key",
            secretAccessKey = "secret",
            region = "us-east-1",
            bucket = "bucket",
            endpoint = "http://minio:9000",
            usePathStyle = false
        )

        val (url, headers) = client.buildSignedUrl(config, "a/x.png")

        assertEquals("http://bucket.minio:9000/a/x.png", url)
        assertEquals("bucket.minio:9000", headers["host"])
    }

    @Test fun defaultHttpsPortIsOmittedFromHost() {
        val config = UploadConfig.S3Config(
            accessKeyId = "key",
            secretAccessKey = "secret",
            region = "us-east-1",
            bucket = "bucket",
            endpoint = "https://minio.example.com:443",
            usePathStyle = true
        )

        val (_, headers) = client.buildSignedUrl(config, "a/x.png")

        assertEquals("minio.example.com", headers["host"])
    }

    @Test fun presignedUrlAlsoCarriesThePort() {
        val config = UploadConfig.S3Config(
            accessKeyId = "key",
            secretAccessKey = "secret",
            region = "us-east-1",
            bucket = "bucket",
            endpoint = "http://minio:9000",
            usePathStyle = true
        )

        val presigned = client.buildPresignedUrl(config, "a/x.png")

        assertTrue(presigned.startsWith("http://minio:9000/bucket/a/x.png?"))
    }
}

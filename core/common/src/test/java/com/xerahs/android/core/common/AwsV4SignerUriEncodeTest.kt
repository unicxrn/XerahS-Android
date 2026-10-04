package com.xerahs.android.core.common

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AwsV4SignerUriEncodeTest {

    @Test fun uriEncodeLeavesAsciiUnreservedCharsAlone() {
        assertEquals(
            "abcXYZ019-_.~",
            AwsV4Signer.uriEncode("abcXYZ019-_.~", preserveSlashes = true)
        )
    }

    @Test fun uriEncodeEncodesAccentedLatinLetter() {
        assertEquals(
            "caf%C3%A9.mp4",
            AwsV4Signer.uriEncode("café.mp4", preserveSlashes = true)
        )
    }

    @Test fun uriEncodeEncodesEmojiAsSurrogatePair() {
        assertEquals(
            "emoji-%F0%9F%98%80.png",
            AwsV4Signer.uriEncode("emoji-😀.png", preserveSlashes = true)
        )
    }

    @Test fun uriEncodePreservesSlashesOnlyWhenRequested() {
        assertEquals("a/b", AwsV4Signer.uriEncode("a/b", preserveSlashes = true))
        assertEquals("a%2Fb", AwsV4Signer.uriEncode("a/b", preserveSlashes = false))
    }

    @Test fun presignRejectsExpiresOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) {
            AwsV4Signer.presign(
                method = "GET",
                url = "https://examplebucket.s3.amazonaws.com/test.txt",
                accessKeyId = "AKIAIOSFODNN7EXAMPLE",
                secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                region = "us-east-1",
                host = "examplebucket.s3.amazonaws.com",
                expiresSeconds = 0
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwsV4Signer.presign(
                method = "GET",
                url = "https://examplebucket.s3.amazonaws.com/test.txt",
                accessKeyId = "AKIAIOSFODNN7EXAMPLE",
                secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                region = "us-east-1",
                host = "examplebucket.s3.amazonaws.com",
                expiresSeconds = 604801
            )
        }
    }

    @Test fun presignRejectsUrlWithExistingQueryString() {
        assertThrows(IllegalArgumentException::class.java) {
            AwsV4Signer.presign(
                method = "GET",
                url = "https://examplebucket.s3.amazonaws.com/test.txt?foo=bar",
                accessKeyId = "AKIAIOSFODNN7EXAMPLE",
                secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                region = "us-east-1",
                host = "examplebucket.s3.amazonaws.com"
            )
        }
    }

    @Test fun presignThreadsServiceThroughSigningKey() {
        val params = mapOf(
            "method" to "GET",
            "url" to "https://examplebucket.s3.amazonaws.com/test.txt",
            "accessKeyId" to "AKIAIOSFODNN7EXAMPLE",
            "secretAccessKey" to "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            "region" to "us-east-1",
            "host" to "examplebucket.s3.amazonaws.com"
        )
        val now = Instant.parse("2013-05-24T00:00:00Z")

        val s3Url = AwsV4Signer.presign(
            method = params["method"]!!,
            url = params["url"]!!,
            accessKeyId = params["accessKeyId"]!!,
            secretAccessKey = params["secretAccessKey"]!!,
            region = params["region"]!!,
            host = params["host"]!!,
            now = now,
            service = "s3"
        )
        val otherServiceUrl = AwsV4Signer.presign(
            method = params["method"]!!,
            url = params["url"]!!,
            accessKeyId = params["accessKeyId"]!!,
            secretAccessKey = params["secretAccessKey"]!!,
            region = params["region"]!!,
            host = params["host"]!!,
            now = now,
            service = "execute-api"
        )

        assertNotEquals(s3Url, otherServiceUrl)
    }
}

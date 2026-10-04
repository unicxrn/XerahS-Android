package com.xerahs.android.core.common

import java.time.Instant
import org.junit.Assert.assertEquals
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
        val now = Instant.parse("2013-05-24T00:00:00Z")

        val url = AwsV4Signer.presign(
            method = "GET",
            url = "https://examplebucket.s3.amazonaws.com/test.txt",
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            host = "examplebucket.s3.amazonaws.com",
            now = now,
            service = "execute-api"
        )

        // Independently computed (not just captured from the implementation): same
        // canonical-request/signing-key algorithm as AwsV4SignerPresignTest's AWS vector,
        // with "execute-api" substituted for "s3" in the signing scope.
        assertEquals(
            "https://examplebucket.s3.amazonaws.com/test.txt?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fexecute-api%2Faws4_request&X-Amz-Date=20130524T000000Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=8742b9c5c572d0e2ef34ea59e84ed8fb3b03bceca1b0a3e38fb5d973173d8aef",
            url
        )
    }
}

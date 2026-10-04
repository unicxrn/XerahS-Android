package com.xerahs.android.core.common

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AwsV4SignerPresignTest {

    @Test fun presignMatchesAwsPublishedExample() {
        val url = AwsV4Signer.presign(
            method = "GET",
            url = "https://examplebucket.s3.amazonaws.com/test.txt",
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            host = "examplebucket.s3.amazonaws.com",
            expiresSeconds = 86400,
            now = Instant.parse("2013-05-24T00:00:00Z")
        )

        assertTrue(url.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"))
        assertTrue(url.contains("X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request"))
        assertTrue(url.contains("X-Amz-Date=20130524T000000Z"))
        assertTrue(url.contains("X-Amz-Expires=86400"))
        assertTrue(url.contains("X-Amz-SignedHeaders=host"))
        assertEquals(
            "https://examplebucket.s3.amazonaws.com/test.txt?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404",
            url
        )
    }
}

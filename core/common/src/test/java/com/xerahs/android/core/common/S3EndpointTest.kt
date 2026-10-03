package com.xerahs.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class S3EndpointTest {
    @Test fun awsVirtualHosted() = assertEquals(
        S3Endpoint.Target("b.s3.eu-west-1.amazonaws.com", "https://b.s3.eu-west-1.amazonaws.com/a/x.png"),
        S3Endpoint.resolve(null, "b", "eu-west-1", false, "a/x.png")
    )

    @Test fun awsDottedBucketUsesPathStyle() = assertEquals(
        S3Endpoint.Target("s3.eu-west-1.amazonaws.com", "https://s3.eu-west-1.amazonaws.com/my.b/a/x.png"),
        S3Endpoint.resolve(null, "my.b", "eu-west-1", false, "a/x.png")
    )

    @Test fun customEndpointPathStyle() = assertEquals(
        S3Endpoint.Target("minio.test", "https://minio.test:9000/b/a/x.png"),
        S3Endpoint.resolve("https://minio.test:9000", "b", "us-east-1", true, "a/x.png")
    )
}

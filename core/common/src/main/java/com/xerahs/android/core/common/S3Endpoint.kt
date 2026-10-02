package com.xerahs.android.core.common

import java.net.URI

object S3Endpoint {
    data class Target(val host: String, val url: String)

    // Same rules S3Uploader has always used. The object key is appended as-is.
    fun resolve(endpoint: String?, bucket: String, region: String, usePathStyle: Boolean, objectKey: String): Target {
        val host: String
        val url: String

        val configEndpoint = endpoint
        if (configEndpoint != null && configEndpoint.isNotEmpty()) {
            // Custom endpoint (MinIO, DigitalOcean Spaces, etc.)
            val trimmedEndpoint = configEndpoint.trimEnd('/')
            if (usePathStyle) {
                host = URI(trimmedEndpoint).host
                url = "$trimmedEndpoint/$bucket/$objectKey"
            } else {
                host = "$bucket.${URI(trimmedEndpoint).host}"
                url = "${trimmedEndpoint.replace(URI(trimmedEndpoint).host, host)}/$objectKey"
            }
        } else {
            // Standard AWS S3
            // Bucket names with dots break virtual-hosted-style because the
            // wildcard SSL cert *.s3.region.amazonaws.com only covers one
            // subdomain level. Use path-style for dotted bucket names.
            if (bucket.contains('.')) {
                host = "s3.$region.amazonaws.com"
                url = "https://$host/$bucket/$objectKey"
            } else {
                host = "$bucket.s3.$region.amazonaws.com"
                url = "https://$host/$objectKey"
            }
        }
        return Target(host, url)
    }
}

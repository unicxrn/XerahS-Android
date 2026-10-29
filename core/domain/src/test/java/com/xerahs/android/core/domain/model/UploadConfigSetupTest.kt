package com.xerahs.android.core.domain.model

import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadConfigSetupTest {
    @Test
    fun s3MissingBucketIsNotConfigured() {
        val c = UploadConfig.S3Config(accessKeyId = "k", secretAccessKey = "s", bucket = "")
        assertFalse(c.isConfiguredForUpload())
        assertTrue(c.copy(bucket = "b").isConfiguredForUpload())
    }

    @Test
    fun imgurNeedsNothing() = assertTrue(UploadConfig.ImgurConfig().isConfiguredForUpload())

    @Test
    fun ftpMissingHostIsNotConfigured() {
        val c = UploadConfig.FtpConfig(host = "", username = "u")
        assertFalse(c.isConfiguredForUpload())
        assertTrue(c.copy(host = "h").isConfiguredForUpload())
    }

    @Test
    fun sftpNeedsPasswordOrKey() {
        val c = UploadConfig.SftpConfig(host = "h", username = "u")
        assertFalse(c.isConfiguredForUpload())
        assertTrue(c.copy(password = "p").isConfiguredForUpload())
        assertTrue(c.copy(keyPath = "/k").isConfiguredForUpload())
    }

    @Test
    fun gistNeedsToken() {
        assertFalse(UploadConfig.GistConfig().isConfiguredForUpload())
        assertTrue(UploadConfig.GistConfig(token = "t").isConfiguredForUpload())
    }

    @Test
    fun nextcloudImmichAndCustomNeedTheirFields() {
        assertFalse(UploadConfig.NextcloudConfig(serverUrl = "u", username = "n").isConfiguredForUpload())
        assertTrue(UploadConfig.NextcloudConfig(serverUrl = "u", username = "n", appPassword = "p").isConfiguredForUpload())
        assertFalse(UploadConfig.ImmichConfig(serverUrl = "u").isConfiguredForUpload())
        assertTrue(UploadConfig.ImmichConfig(serverUrl = "u", apiKey = "k").isConfiguredForUpload())
        assertFalse(UploadConfig.CustomUploaderConfig().isConfiguredForUpload())
        assertTrue(UploadConfig.CustomUploaderConfig(CustomUploaderSpec(requestURL = "https://x")).isConfiguredForUpload())
    }
}

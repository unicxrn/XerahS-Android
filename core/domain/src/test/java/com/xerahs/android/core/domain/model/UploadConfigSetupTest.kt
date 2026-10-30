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
    fun imgurNeedsClientId() {
        assertFalse(UploadConfig.ImgurConfig().isConfiguredForUpload())
        assertTrue(UploadConfig.ImgurConfig(clientId = "id").isConfiguredForUpload())
        val signedIn = UploadConfig.ImgurConfig(clientId = "id", useAnonymous = false)
        assertFalse(signedIn.isConfiguredForUpload())
        assertTrue(signedIn.copy(accessToken = "t").isConfiguredForUpload())
    }

    @Test
    fun s3NeedsRegion() {
        val c = UploadConfig.S3Config(accessKeyId = "k", secretAccessKey = "s", bucket = "b", region = "")
        assertFalse(c.isConfiguredForUpload())
    }

    @Test
    fun anonymousFtpIsAllowed() =
        assertTrue(UploadConfig.FtpConfig(host = "h", username = "").isConfiguredForUpload())

    @Test
    fun profileConfigCountsAsSetup() {
        val empty = UploadConfig.CustomUploaderConfig()
        val good = UploadConfig.CustomUploaderConfig(CustomUploaderSpec(requestURL = "https://x"))
        assertFalse(isSetupComplete(empty, emptyList()))
        assertFalse(isSetupComplete(null, listOf(empty)))
        assertTrue(isSetupComplete(empty, listOf(empty, good)))
        assertTrue(isSetupComplete(good, emptyList()))
    }

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

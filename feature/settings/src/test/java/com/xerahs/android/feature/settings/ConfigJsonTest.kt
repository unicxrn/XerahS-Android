package com.xerahs.android.feature.settings

import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigJsonTest {
    @Test fun roundTripsEveryConfigType() {
        listOf(
            UploadDestination.IMGUR to UploadConfig.ImgurConfig("id", "sec", "at", null, false),
            UploadDestination.S3 to UploadConfig.S3Config("AK", "SK", "eu-west-1", "b", "https://e", null, "p/", "public-read", true),
            UploadDestination.FTP to UploadConfig.FtpConfig("h", 2121, "u", "pw", "/r", true, false, "https://x"),
            UploadDestination.SFTP to UploadConfig.SftpConfig("h", 22, "u", "pw", "/k", "kp", "/r", ""),
            UploadDestination.CUSTOM_HTTP to UploadConfig.CustomUploaderConfig(CustomUploaderSpec(name = "C", requestURL = "https://c.test")),
            UploadDestination.NEXTCLOUD to UploadConfig.NextcloudConfig("https://nc", "a", "pw", "F", false),
            UploadDestination.IMMICH to UploadConfig.ImmichConfig("https://im", "k", false),
            UploadDestination.GITHUB_GIST to UploadConfig.GistConfig("t", true),
        ).forEach { (dest, config) ->
            assertEquals(dest.name, config, ConfigJson.fromJson(dest, ConfigJson.toJson(config)))
        }
    }

    @Test fun localHasNoConfig() = assertEquals(null, ConfigJson.fromJson(UploadDestination.LOCAL, com.google.gson.JsonObject()))
}

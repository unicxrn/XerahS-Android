package com.xerahs.android.core.data.importer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xerahs.android.core.domain.model.INSECURE_HTTP_WARNING
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploaderFileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploaderImportParsingTest {
    @Test fun detectsByExtensionThenContent() {
        assertEquals(UploaderFileKind.SXCU, UploaderImportParsing.detect(ByteArray(0), "a.SXCU"))
        assertEquals(UploaderFileKind.XSDC, UploaderImportParsing.detect(ByteArray(0), "a.xsdc"))
        assertEquals(UploaderFileKind.SXCU, UploaderImportParsing.detect("""{"requesturl":"https://x"}""".toByteArray(), null))
        assertEquals(UploaderFileKind.XSDC, UploaderImportParsing.detect("""{"Format":"XerahS.DestinationConfig"}""".toByteArray(), "download.bin"))
        assertEquals(UploaderFileKind.UNKNOWN, UploaderImportParsing.detect("hello".toByteArray(), "notes.txt"))
    }

    @Test fun sxcuDraftWarnsAboutPlainHttp() {
        val draft = UploaderImportParsing.sxcuDraft("""{"Name":"H","RequestURL":"http://h.test/u"}""".toByteArray()).getOrThrow()
        assertEquals("H", draft.name)
        assertEquals(UploadDestination.CUSTOM_HTTP, draft.destination)
        assertTrue(draft.config is UploadConfig.CustomUploaderConfig)
        assertEquals(listOf(INSECURE_HTTP_WARNING), draft.warnings)
    }

    @Test fun mapsS3AndSkipsUnsupported() {
        val dests = XsdcDecoder.decode(XsdcDecoderTest.encrypt(XsdcDecoderTest.s3Payload(), "p"), "p".toCharArray())
        val result = UploaderImportParsing.xsdcDrafts(dests)

        assertEquals(1, result.drafts.size)
        val draft = result.drafts.single()
        assertEquals("Desktop S3", draft.name)
        assertEquals(UploadDestination.S3, draft.destination)
        assertTrue(draft.makeDefault)
        assertEquals(
            UploadConfig.S3Config(
                accessKeyId = "AK", secretAccessKey = "SK", region = "eu-west-1", bucket = "b1",
                endpoint = null, customUrl = "https://cdn.test", acl = "public-read", usePathStyle = false
            ),
            draft.config
        )
        assertEquals(listOf("DB (dropbox isn't supported on Android yet)"), result.skipped)
    }

    @Test fun addsHttpsSchemeToBareHostEndpoint() {
        val payload = JsonObject().apply {
            addProperty("Format", "XerahS.DestinationConfig.Payload")
            addProperty("FormatVersion", 1)
            add("Destinations", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("ProviderId", "amazons3"); addProperty("DisplayName", "Minio")
                    addProperty("IsDefault", false)
                    add("Config", JsonObject().apply {
                        addProperty("AuthMode", "AccessKeys"); addProperty("AccessKeyId", "AK")
                        addProperty("SecretAccessKey", "SK"); addProperty("BucketName", "b1")
                        addProperty("Region", "us-east-1"); addProperty("Endpoint", "minio.example.com:9000")
                        addProperty("UsePathStyle", true); addProperty("UseCustomDomain", false)
                        addProperty("CustomDomain", ""); addProperty("SetPublicAcl", false)
                    })
                })
            })
        }.toString()
        val dests = XsdcDecoder.decode(XsdcDecoderTest.encrypt(payload, "p"), "p".toCharArray())
        val draft = UploaderImportParsing.xsdcDrafts(dests).drafts.single()
        val config = draft.config as UploadConfig.S3Config
        assertEquals("https://minio.example.com:9000", config.endpoint)
    }
}

package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.domain.model.UploadConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImmichUploaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val uploader = ImmichUploader(OkHttpClient())

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun base() = server.url("/").toString().trimEnd('/')
    private fun config(share: Boolean = true) = UploadConfig.ImmichConfig(base(), "k3y", share)
    private fun file() = tmp.newFile("shot.png").apply { writeText("PNG") }

    @Test fun uploadsAndCreatesShareLink() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"a1","status":"created"}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"key":"k9"}"""))
        val result = uploader.upload(file(), config(), "shot.png")

        assertTrue(result.errorMessage, result.success)
        assertEquals("${base()}/share/k9", result.url)
        val upload = server.takeRequest()
        assertEquals("/api/assets", upload.path)
        assertEquals("k3y", upload.getHeader("x-api-key"))
        val body = upload.body.readUtf8()
        assertTrue(body.contains("name=\"assetData\"; filename=\"shot.png\"") && body.contains("xerahs-android"))
        val link = server.takeRequest()
        assertEquals("/api/shared-links", link.path)
        assertTrue(link.body.readUtf8().contains("\"assetIds\":[\"a1\"]"))
    }

    @Test fun withoutShareReturnsPhotoUrl() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"a1","status":"duplicate"}"""))
        assertEquals("${base()}/photos/a1", uploader.upload(file(), config(share = false), "shot.png").url)
    }

    @Test fun reportsHttpErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad key"))
        val result = uploader.upload(file(), config(), "shot.png")
        assertFalse(result.success)
        assertTrue(result.errorMessage!!.startsWith("Immich HTTP 401"))
    }
}

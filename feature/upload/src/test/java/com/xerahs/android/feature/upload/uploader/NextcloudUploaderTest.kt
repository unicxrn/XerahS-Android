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

class NextcloudUploaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val uploader = NextcloudUploader(OkHttpClient())

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun config(share: Boolean = true) =
        UploadConfig.NextcloudConfig(server.url("/").toString(), "alice", "pw", "XerahS", share)
    private fun file() = tmp.newFile("shot.png").apply { writeText("PNG") }

    @Test fun uploadsAndShares() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setBody("""{"ocs":{"data":{"url":"https://nc.test/s/abc"}}}"""))
        val result = uploader.upload(file(), config(), "shot.png")

        assertTrue(result.errorMessage, result.success)
        assertEquals("https://nc.test/s/abc", result.url)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/remote.php/dav/files/alice/XerahS/shot.png", put.path)
        assertTrue(put.getHeader("Authorization")!!.startsWith("Basic "))
        val share = server.takeRequest()
        assertEquals("POST", share.method)
        assertTrue(share.path!!.startsWith("/ocs/v2.php/apps/files_sharing/api/v1/shares"))
        assertEquals("true", share.getHeader("OCS-APIRequest"))
        val body = share.body.readUtf8()
        assertTrue(body, body.contains("path=%2FXerahS%2Fshot.png") && body.contains("shareType=3"))
    }

    @Test fun createsMissingFolderThenRetries() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setBody("""{"ocs":{"data":{"url":"https://nc.test/s/x"}}}"""))
        assertTrue(uploader.upload(file(), config(), "shot.png").success)
        assertEquals(listOf("PUT", "MKCOL", "PUT", "POST"), List(4) { server.takeRequest().method })
    }

    @Test fun withoutShareReturnsWebDavUrl() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val result = uploader.upload(file(), config(share = false), "shot.png")
        assertEquals(server.url("/remote.php/dav/files/alice/XerahS/shot.png").toString(), result.url)
        assertEquals(1, server.requestCount)
    }

    @Test fun reportsHttpErrorsWithoutSecrets() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))
        val result = uploader.upload(file(), config(), "shot.png")
        assertFalse(result.success)
        assertTrue(result.errorMessage!!.startsWith("Nextcloud HTTP 401"))
        assertFalse(result.errorMessage!!.contains("pw"))
    }
}

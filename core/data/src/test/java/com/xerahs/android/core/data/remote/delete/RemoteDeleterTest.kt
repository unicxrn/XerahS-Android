package com.xerahs.android.core.data.remote.delete

import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.repository.OpenInBrowserException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteDeleterTest {
    private val server = MockWebServer()
    private val deleter = RemoteDeleter(OkHttpClient())

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun item(dest: UploadDestination, remoteKey: String? = null, deleteUrl: String? = null) = HistoryItem(
        id = "1", filePath = "/x", uploadDestination = dest, timestamp = 0, fileName = "x.png",
        remoteKey = remoteKey, deleteUrl = deleteUrl
    )

    @Test fun canDeleteRules() {
        assertTrue(deleter.canDelete(item(UploadDestination.CUSTOM_HTTP, deleteUrl = "https://d")))
        assertFalse(deleter.canDelete(item(UploadDestination.CUSTOM_HTTP)))
        assertTrue(deleter.canDelete(item(UploadDestination.S3, remoteKey = "a/x.png")))
        assertFalse(deleter.canDelete(item(UploadDestination.S3)))
        assertFalse(deleter.canDelete(item(UploadDestination.IMGUR, remoteKey = "k", deleteUrl = "https://d")))
        assertFalse(deleter.canDelete(item(UploadDestination.LOCAL, remoteKey = "/x")))
    }

    @Test fun deletionUrlAlwaysOpensInBrowser() = runBlocking {
        // Many deletion-page URLs are HTML confirm pages that return 200 without deleting,
        // so these are never auto-GETed - always hand off to the user, and make no request.
        val url = server.url("/delete/abc").toString()
        val e = runCatching { deleter.delete(item(UploadDestination.CUSTOM_HTTP, deleteUrl = url), UploadConfig.CustomUploaderConfig()) }.exceptionOrNull()
        assertTrue(e is OpenInBrowserException)
        assertEquals(url, (e as OpenInBrowserException).url)
        assertEquals(0, server.requestCount)
    }

    @Test fun nextcloudDeleteToleratesMissingFile() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val config = UploadConfig.NextcloudConfig(server.url("/").toString(), "alice", "pw")
        deleter.delete(item(UploadDestination.NEXTCLOUD, remoteKey = "XerahS/x.png"), config)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/remote.php/dav/files/alice/XerahS/x.png", req.path)
        assertTrue(req.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test fun s3DeleteIsSigned() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val config = UploadConfig.S3Config(
            accessKeyId = "AK", secretAccessKey = "SK", region = "us-east-1", bucket = "b",
            endpoint = server.url("/").toString().trimEnd('/'), usePathStyle = true
        )
        deleter.delete(item(UploadDestination.S3, remoteKey = "a/x.png"), config)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/b/a/x.png", req.path)
        assertTrue(req.getHeader("Authorization")!!.startsWith("AWS4-HMAC-SHA256"))
    }

    @Test fun s3NoSuchBucket404IsFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("NoSuchBucket"))
        val config = UploadConfig.S3Config(accessKeyId = "AK", secretAccessKey = "SK", bucket = "b",
            endpoint = server.url("/").toString().trimEnd('/'), usePathStyle = true)
        val e = runCatching { deleter.delete(item(UploadDestination.S3, remoteKey = "a/x.png"), config) }.exceptionOrNull()
        assertTrue(e!!.message!!.startsWith("S3 HTTP 404"))
    }

    @Test fun s3ErrorIsReported() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("AccessDenied"))
        val config = UploadConfig.S3Config(accessKeyId = "AK", secretAccessKey = "SK", bucket = "b",
            endpoint = server.url("/").toString().trimEnd('/'), usePathStyle = true)
        val e = runCatching { deleter.delete(item(UploadDestination.S3, remoteKey = "a/x.png"), config) }.exceptionOrNull()
        assertTrue(e!!.message!!.startsWith("S3 HTTP 403"))
    }
}

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

class GistUploaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private lateinit var uploader: GistUploader

    @Before fun setUp() {
        server.start()
        uploader = GistUploader(OkHttpClient()).apply { apiBase = server.url("/").toString().trimEnd('/') }
    }
    @After fun tearDown() = server.shutdown()

    @Test fun createsSecretGist() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"html_url":"https://gist.github.com/u/1"}"""))
        val file = tmp.newFile("note.txt").apply { writeText("say \"hi\"") }
        val result = uploader.upload(file, UploadConfig.GistConfig("tok", isPublic = false), "note.txt")

        assertEquals("https://gist.github.com/u/1", result.url)
        val req = server.takeRequest()
        assertEquals("/gists", req.path)
        assertEquals("Bearer tok", req.getHeader("Authorization"))
        assertEquals("""{"public":false,"files":{"note.txt":{"content":"say \"hi\""}}}""", req.body.readUtf8())
    }

    @Test fun rejectsFilesOverOneMegabyte() = runBlocking {
        val file = tmp.newFile("big.txt").apply { writeBytes(ByteArray(1_000_001) { 'a'.code.toByte() }) }
        val result = uploader.upload(file, UploadConfig.GistConfig("tok"), "big.txt")
        assertFalse(result.success)
        assertEquals(0, server.requestCount)
    }

    @Test fun reportsHttpErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Bad credentials"))
        val result = uploader.upload(tmp.newFile("a.txt").apply { writeText("x") }, UploadConfig.GistConfig("tok"), "a.txt")
        assertTrue(result.errorMessage!!.startsWith("GitHub HTTP 401"))
    }
}

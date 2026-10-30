package com.xerahs.android.feature.upload.intake

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UrlDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val downloader = UrlDownloader(OkHttpClient())

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun downloadsWithResolvedName() = runBlocking {
        server.enqueue(MockResponse().setBody("hello").setHeader("Content-Type", "image/png"))
        val dir = tmp.newFolder()
        var last = 0f
        val file = downloader.download(server.url("/pic").toString(), dir) { last = it }
        assertEquals("pic.png", file.name)
        assertEquals("hello", file.readText())
        assertEquals(1f, last)
    }

    @Test fun contentLengthOverCapRefused() {
        server.enqueue(MockResponse().setBody("0123456789"))
        val dir = tmp.newFolder()
        val e = runCatching {
            runBlocking { downloader.download(server.url("/big.bin").toString(), dir, maxBytes = 5) {} }
        }.exceptionOrNull()
        assertTrue(e is FileTooLargeException)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun chunkedBodyOverCapRefusedAndPartialDeleted() {
        // Chunked: no Content-Length, so only the running count can catch it.
        server.enqueue(MockResponse().setChunkedBody(Buffer().write(ByteArray(64 * 1024)), 1024))
        val dir = tmp.newFolder()
        val e = runCatching {
            runBlocking { downloader.download(server.url("/stream").toString(), dir, maxBytes = 10_000) {} }
        }.exceptionOrNull()
        assertTrue(e is FileTooLargeException)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun httpErrorFails() {
        server.enqueue(MockResponse().setResponseCode(404))
        val dir = tmp.newFolder()
        val e = runCatching { runBlocking { downloader.download(server.url("/x").toString(), dir) {} } }.exceptionOrNull()
        assertTrue(e is IOException)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun brokenConnectionDeletesPartial() {
        server.enqueue(
            MockResponse().setBody(Buffer().write(ByteArray(64 * 1024)))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        val dir = tmp.newFolder()
        val e = runCatching { runBlocking { downloader.download(server.url("/x.bin").toString(), dir) {} } }.exceptionOrNull()
        assertTrue(e is IOException)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun nonHttpSchemeRefused() {
        val dir = tmp.newFolder()
        val e = runCatching { runBlocking { downloader.download("file:///etc/passwd", dir) {} } }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test fun cancelAbortsStalledDownloadAndDeletesPartial() = runBlocking {
        // 64 KB sent 1 KB per second: the read blocks long after the cancel.
        server.enqueue(
            MockResponse().setBody(Buffer().write(ByteArray(64 * 1024)))
                .throttleBody(1024, 1, TimeUnit.SECONDS)
        )
        val dir = tmp.newFolder()
        val started = CompletableDeferred<Unit>()
        val job = async(Dispatchers.Default) {
            downloader.download(server.url("/slow.bin").toString(), dir) { if (it > 0f) started.complete(Unit) }
        }
        withTimeout(5_000) { started.await() }
        withTimeout(3_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun httpsLinkEndingOnHttpIsRefused() {
        // MockWebServer only speaks http here, so an interceptor plays the https to http redirect:
        // the call starts on https and the final response comes from plain http.
        server.enqueue(MockResponse().setBody("secret").setHeader("Content-Type", "image/png"))
        val target = server.url("/landing.png")
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(target).build()) }
            .build()
        val dir = tmp.newFolder()
        val e = runCatching {
            runBlocking { UrlDownloader(client).download("https://example.test/pic.png", dir) {} }
        }.exceptionOrNull()
        assertTrue(e is InsecureRedirectException)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun cleanUpDeletesFolderOnFailure() {
        val dir = tmp.newFolder("shared_fail")
        val e = runCatching {
            runBlocking {
                downloadOrCleanUp(dir) { d -> File(d, "part.bin").writeText("x"); throw IOException("boom") }
            }
        }.exceptionOrNull()
        assertTrue(e is IOException)
        assertFalse(dir.exists())
    }

    @Test fun cleanUpDeletesFolderOnCancel() = runBlocking {
        val dir = tmp.newFolder("shared_cancel")
        val written = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            downloadOrCleanUp(dir) { d ->
                File(d, "part.bin").writeText("x")
                written.complete(Unit)
                awaitCancellation()
            }
        }
        written.await()
        job.cancelAndJoin()
        assertFalse(dir.exists())
    }

    @Test fun cleanUpKeepsFolderOnSuccess() = runBlocking {
        val dir = tmp.newFolder("shared_ok")
        val file = downloadOrCleanUp(dir) { d -> File(d, "ok.bin").apply { writeText("x") } }
        assertTrue(file.exists())
    }
}

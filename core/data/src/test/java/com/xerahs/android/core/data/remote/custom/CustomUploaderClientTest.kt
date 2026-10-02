package com.xerahs.android.core.data.remote.custom

import com.xerahs.android.core.common.sxcu.CustomBodyType
import com.xerahs.android.core.common.sxcu.CustomDestinationType
import com.xerahs.android.core.common.sxcu.CustomUploaderSpec
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomUploaderClientTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val client = CustomUploaderClient(OkHttpClient())

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun file(name: String = "shot.png", content: String = "PNGDATA") =
        tmp.newFile(name).apply { writeText(content) }

    @Test fun multipartUploadParsesJsonUrlsAndSendsArgsHeadersParams() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"url":"https://i.test/a.png","del":"https://i.test/d/a"}}"""))
        val spec = CustomUploaderSpec(
            requestURL = server.url("/up").toString(),
            parameters = mapOf("ttl" to "60"),
            headers = mapOf("Authorization" to "Bearer {inputbox:Token}"),
            arguments = mapOf("name" to "{filename}"),
            fileFormName = "image",
            url = "{json:data.url}",
            deletionURL = "{json:data.del}",
        )
        val outcome = client.execute(spec, CustomUploadInput(file(), "shot.png", inputValues = mapOf("Token" to "t1")))

        assertEquals(CustomUploadOutcome.Success("https://i.test/a.png", null, "https://i.test/d/a"), outcome)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("60", req.requestUrl!!.queryParameter("ttl"))
        assertEquals("Bearer t1", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"image\"; filename=\"shot.png\""))
        assertTrue(body.contains("Content-Type: image/png"))
        assertTrue(body.contains("name=\"name\"") && body.contains("shot.png"))
        assertTrue(body.contains("PNGDATA"))
    }

    @Test fun formUrlEncodedTextUploaderUsesInput() = runBlocking {
        server.enqueue(MockResponse().setBody("https://p.test/xyz"))
        val spec = CustomUploaderSpec(
            requestURL = server.url("/paste").toString(),
            destinationTypes = setOf(CustomDestinationType.TEXT),
            body = CustomBodyType.FORM_URL_ENCODED,
            arguments = mapOf("content" to "{input}"),
            fileFormName = "",
        )
        val outcome = client.execute(spec, CustomUploadInput(null, "note.txt", input = "a b&c"))
        assertEquals(CustomUploadOutcome.Success("https://p.test/xyz", null, null), outcome)
        assertEquals("content=a%20b%26c", server.takeRequest().body.readUtf8())
    }

    @Test fun jsonBodyEscapesInput() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"short":"https://s.test/1"}"""))
        val spec = CustomUploaderSpec(
            requestURL = server.url("/s").toString(),
            body = CustomBodyType.JSON,
            data = """{"url":"{input}"}""",
            url = "{json:short}",
        )
        client.execute(spec, CustomUploadInput(null, "", input = "https://x.test/?q=\"a\""))
        val req = server.takeRequest()
        assertEquals("""{"url":"https://x.test/?q=\"a\""}""", req.body.readUtf8())
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test fun binaryPutSendsRawFile() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val spec = CustomUploaderSpec(
            requestMethod = "PUT",
            requestURL = server.url("/b/").toString() + "{filename}",
            body = CustomBodyType.BINARY,
            url = "https://b.test/{filename}",
        )
        val outcome = client.execute(spec, CustomUploadInput(file("doc.pdf", "PDF"), "doc.pdf"))
        assertEquals(CustomUploadOutcome.Success("https://b.test/doc.pdf", null, null), outcome)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/b/doc.pdf", req.path)
        assertEquals("PDF", req.body.readUtf8())
        assertEquals("application/pdf", req.getHeader("Content-Type"))
    }

    @Test fun httpErrorUsesErrorMessageTemplate() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"Bad key"}}"""))
        val spec = CustomUploaderSpec(requestURL = server.url("/").toString(), errorMessage = "{json:error.message}")
        assertEquals(CustomUploadOutcome.Failure("Bad key"), client.execute(spec, CustomUploadInput(file(), "shot.png")))
    }

    @Test fun httpErrorWithoutTemplateReportsCodeAndBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val spec = CustomUploaderSpec(requestURL = server.url("/").toString())
        assertEquals(CustomUploadOutcome.Failure("HTTP 500: boom"), client.execute(spec, CustomUploadInput(file(), "shot.png")))
    }

    @Test fun missingJsonPathIsAFailureNotAnEmptyUrl() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"other":1}"""))
        val spec = CustomUploaderSpec(requestURL = server.url("/").toString(), url = "{json:data.link}")
        val outcome = client.execute(spec, CustomUploadInput(file(), "shot.png"))
        assertTrue(outcome is CustomUploadOutcome.Failure)
        assertTrue((outcome as CustomUploadOutcome.Failure).message.contains("json:data.link"))
    }

    @Test fun blankUrlTemplateFallsBackToResponseBody() = runBlocking {
        server.enqueue(MockResponse().setBody("  https://r.test/1\n"))
        val spec = CustomUploaderSpec(requestURL = server.url("/").toString())
        assertEquals(CustomUploadOutcome.Success("https://r.test/1", null, null),
            client.execute(spec, CustomUploadInput(file(), "shot.png")))
    }

    @Test fun brokenThumbnailTemplateDoesNotFailUpload() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"url":"https://i.test/1"}"""))
        val spec = CustomUploaderSpec(requestURL = server.url("/").toString(), url = "{json:url}", thumbnailURL = "{json:thumb}")
        assertEquals(CustomUploadOutcome.Success("https://i.test/1", null, null),
            client.execute(spec, CustomUploadInput(file(), "shot.png")))
    }
}

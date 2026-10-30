package com.xerahs.android.feature.upload.intake

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Downloads an http(s) link into a folder for upload, with a size cap. */
class UrlDownloader @Inject constructor(private val client: OkHttpClient) {

    /**
     * Streams [url] into [dir] and returns the file. [onProgress] gets 0..1, or -1 when the size
     * is unknown. Refuses anything over [maxBytes], both from Content-Length and while reading.
     * Cancelling the coroutine aborts the request. The partial file is deleted on any failure.
     */
    suspend fun download(
        url: String,
        dir: File,
        maxBytes: Long = MAX_LINK_DOWNLOAD_BYTES,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val httpUrl = singleUrlOrNull(url)?.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Only http and https links can be downloaded")
        val call = client.newCall(Request.Builder().url(httpUrl).get().build())
        coroutineScope {
            // A blocked read does not notice cancellation, so cancel the call itself.
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("The server answered ${response.code}")
                    val body = response.body ?: throw IOException("The server sent no file")
                    val length = body.contentLength()
                    if (length > 0) checkDownloadSize(length, maxBytes)
                    dir.mkdirs()
                    val name = resolveFileName(
                        response.request.url.toString(),
                        response.header("Content-Disposition"),
                        body.contentType()?.toString()
                    )
                    val file = File(dir, name)
                    try {
                        body.byteStream().use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                var lastPercent = -1
                                if (length <= 0) onProgress(-1f)
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    checkDownloadSize(total, maxBytes)
                                    output.write(buffer, 0, n)
                                    if (length > 0) {
                                        val percent = (total * 100 / length).toInt()
                                        if (percent != lastPercent) {
                                            lastPercent = percent
                                            onProgress((total.toFloat() / length).coerceAtMost(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        file.delete()
                        throw t
                    }
                    onProgress(1f)
                    file
                }
            } catch (e: IOException) {
                // A cancel shows up as a closed socket; report it as the cancellation it is.
                ensureActive()
                throw e
            } finally {
                watcher.cancel()
            }
        }
    }
}

package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.data.remote.http.await
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class HttpReply(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

// Sends the request (cancellable) and reads at most 1 MB of the body.
internal suspend fun OkHttpClient.send(request: Request): HttpReply =
    newCall(request).await().use { HttpReply(it.code, it.peekBody(1_000_000L).string()) }

internal fun hostError(host: String, reply: HttpReply) = "$host HTTP ${reply.code}: ${reply.body.take(200)}"

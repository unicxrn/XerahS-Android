package com.xerahs.android.feature.upload.uploader

import com.google.gson.JsonParser
import com.xerahs.android.core.common.file.MimeTypes
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import kotlinx.coroutines.CancellationException
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NextcloudUploader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun upload(file: File, config: UploadConfig.NextcloudConfig, remoteFileName: String): UploadResult = try {
        val base = config.serverUrl.trim().toHttpUrl()
        val auth = Credentials.basic(config.username, config.appPassword)
        val folderParts = config.folder.split('/').filter { it.isNotBlank() }
        val fileUrl = davUrl(base, config.username, folderParts + remoteFileName)
        val body = file.asRequestBody(MimeTypes.fromFileName(remoteFileName).toMediaType())

        var put = okHttpClient.send(Request.Builder().url(fileUrl).header("Authorization", auth).put(body).build())
        if ((put.code == 404 || put.code == 409) && folderParts.isNotEmpty()) {
            createFolders(base, config.username, folderParts, auth)
            put = okHttpClient.send(Request.Builder().url(fileUrl).header("Authorization", auth).put(body).build())
        }
        when {
            !put.ok -> failure(hostError("Nextcloud", put))
            !config.publicShare -> success(fileUrl.toString())
            else -> share(base, auth, "/" + (folderParts + remoteFileName).joinToString("/"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        failure("Nextcloud network error: ${e.message ?: e.javaClass.simpleName}")
    } catch (e: IllegalArgumentException) {
        failure("Nextcloud request is invalid (check the server URL and app password)")
    }

    private fun davUrl(base: HttpUrl, user: String, segments: List<String>): HttpUrl =
        base.newBuilder().addPathSegments("remote.php/dav/files").addPathSegment(user)
            .apply { segments.forEach { addPathSegment(it) } }.build()

    // MKCOL each level; 405 means it already exists.
    private suspend fun createFolders(base: HttpUrl, user: String, parts: List<String>, auth: String) {
        for (i in parts.indices) {
            val url = davUrl(base, user, parts.take(i + 1))
            okHttpClient.send(Request.Builder().url(url).header("Authorization", auth).method("MKCOL", null).build())
        }
    }

    private suspend fun share(base: HttpUrl, auth: String, path: String): UploadResult {
        val url = base.newBuilder().addPathSegments("ocs/v2.php/apps/files_sharing/api/v1/shares")
            .addQueryParameter("format", "json").build()
        val reply = okHttpClient.send(
            Request.Builder().url(url)
                .header("Authorization", auth)
                .header("OCS-APIRequest", "true")
                .post(FormBody.Builder().add("path", path).add("shareType", "3").build())
                .build()
        )
        if (!reply.ok) return failure("Uploaded, but sharing failed: " + hostError("Nextcloud", reply))
        val link = runCatching {
            JsonParser.parseString(reply.body).asJsonObject
                .getAsJsonObject("ocs").getAsJsonObject("data").get("url").asString
        }.getOrNull()
        return if (link.isNullOrBlank()) failure("Uploaded, but Nextcloud returned no share link") else success(link)
    }

    private fun success(url: String) = UploadResult(success = true, url = url, destination = UploadDestination.NEXTCLOUD)
    private fun failure(message: String) = UploadResult(success = false, errorMessage = message, destination = UploadDestination.NEXTCLOUD)
}

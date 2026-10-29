package com.xerahs.android.feature.upload.worker

import com.jcraft.jsch.JSchException
import com.xerahs.android.core.data.remote.transfer.HostKeyChangedException
import java.io.FileNotFoundException
import java.io.IOException

enum class FailureKind { RETRY, PERMANENT }

/** Thrown when EXIF location data can't be removed; the file must not go out with it. */
class LocationStripException(cause: Throwable? = null) :
    Exception("Couldn't remove location data, upload cancelled", cause)

/**
 * Decides whether a failed upload is worth retrying. Client errors (except 408 and 429), auth
 * failures, a changed SSH host key and missing files are permanent. Network errors, 5xx and
 * anything unknown retry.
 */
object UploadErrorPolicy {
    fun classify(httpCode: Int?, error: Throwable? = null): FailureKind {
        if (httpCode != null) {
            return when {
                httpCode == 408 || httpCode == 429 -> FailureKind.RETRY
                httpCode in 400..499 -> FailureKind.PERMANENT
                else -> FailureKind.RETRY
            }
        }
        return when (error) {
            null -> FailureKind.RETRY
            is FileNotFoundException,
            is HostKeyChangedException,
            is LocationStripException,
            is SecurityException,
            is IllegalArgumentException -> FailureKind.PERMANENT
            is JSchException -> if (error.message.orEmpty().startsWith("Auth")) FailureKind.PERMANENT else FailureKind.RETRY
            is IOException -> if (error.message.orEmpty().startsWith("FTP login failed")) FailureKind.PERMANENT else FailureKind.RETRY
            else -> FailureKind.RETRY
        }
    }
}

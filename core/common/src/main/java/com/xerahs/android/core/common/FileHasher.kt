package com.xerahs.android.core.common

import java.io.File
import java.security.MessageDigest

data class FileHashes(val md5: String, val sha1: String, val sha256: String)

object FileHasher {
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeAll(input: java.io.InputStream): FileHashes {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        input.buffered().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                md5.update(buffer, 0, read); sha1.update(buffer, 0, read); sha256.update(buffer, 0, read)
            }
        }
        fun hex(d: MessageDigest) = d.digest().joinToString("") { "%02x".format(it) }
        return FileHashes(hex(md5), hex(sha1), hex(sha256))
    }

    fun matches(expected: String, actual: String): Boolean = expected.trim().equals(actual.trim(), ignoreCase = true)
}

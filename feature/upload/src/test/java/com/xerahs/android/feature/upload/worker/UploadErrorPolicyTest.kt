package com.xerahs.android.feature.upload.worker

import com.jcraft.jsch.JSchException
import com.xerahs.android.core.data.remote.transfer.HostKeyChangedException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class UploadErrorPolicyTest {
    @Test fun clientErrorsArePermanent() {
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(401, null))
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(403))
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(404))
    }

    @Test fun timeoutsRateLimitsAndServerErrorsRetry() {
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(408))
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(429))
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(500))
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(null, IOException()))
    }

    @Test fun missingFileIsPermanent() {
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, FileNotFoundException()))
    }

    @Test fun changedHostKeyIsPermanent() {
        val e = HostKeyChangedException("example.com:22", "SHA256:old", "SHA256:new")
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, e))
    }

    @Test fun authFailuresArePermanent() {
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, JSchException("Auth fail")))
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, IOException("FTP login failed: 530")))
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(null, JSchException("timeout: socket is not established")))
    }

    @Test fun locationStripFailureIsPermanent() {
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, LocationStripException()))
    }

    @Test fun unknownFailureRetries() {
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(null, null))
    }
}

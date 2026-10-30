package com.xerahs.android.feature.upload.worker

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.SftpException
import com.xerahs.android.core.common.sxcu.SyntaxEvaluationException
import com.xerahs.android.core.data.remote.transfer.FtpLoginException
import com.xerahs.android.core.data.remote.transfer.HostKeyChangedException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

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
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, JSchException("Auth cancel")))
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(null, JSchException("timeout: socket is not established")))
    }

    @Test fun locationStripFailureIsPermanent() {
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, LocationStripException()))
    }

    @Test fun unknownFailureRetries() {
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(null, null))
    }

    @Test fun uploaderFlagWinsOverCodeAndError() {
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(500, IOException(), permanent = true))
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(401, null, permanent = false))
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(429, null, permanent = null))
    }

    @Test fun ftpLoginRejectedIsPermanentButBusyRetries() {
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, FtpLoginException(530, "530 Login incorrect")))
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(null, FtpLoginException(421, "421 Too many users")))
    }

    @Test fun ftpLoginTextAloneIsNotTreatedAsAuth() {
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(null, IOException("FTP login failed: 530")))
    }

    @Test fun tlsAndCertificateProblemsArePermanent() {
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, SSLHandshakeException("bad cert")))
        val wrapped = IOException("handshake", RuntimeException(CertificateException("untrusted")))
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, wrapped))
    }

    @Test fun sftpPermissionAndBadKeyArePermanent() {
        val denied = SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "Permission denied")
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, denied))
        assertEquals(FailureKind.RETRY, UploadErrorPolicy.classify(null, SftpException(ChannelSftp.SSH_FX_FAILURE, "Failure")))
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, JSchException("invalid privatekey: [B@1")))
    }

    @Test fun customUploaderSyntaxErrorIsPermanent() {
        assertEquals(FailureKind.PERMANENT, UploadErrorPolicy.classify(null, SyntaxEvaluationException("{json:x}", "bad")))
    }
}

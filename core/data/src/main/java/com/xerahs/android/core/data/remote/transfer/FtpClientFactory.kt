package com.xerahs.android.core.data.remote.transfer

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.util.TrustManagerUtils
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/**
 * Connects and logs in an FTP client. FTPS is explicit TLS with hostname verification,
 * and the data channel is protected too (PBSZ 0, PROT P). Blocking: call off the main thread.
 * The caller owns the returned client and must log out and disconnect it.
 */
@Singleton
class FtpClientFactory @Inject constructor() {

    fun connect(host: String, port: Int, user: String, password: String, ftps: Boolean, passive: Boolean): FTPClient {
        val client: FTPClient = if (ftps) {
            FTPSClient(false).apply {
                // Validate the certificate chain against the platform trust store. The
                // commons-net default only checks validity dates, so self-signed FTPS
                // servers used to pass; they are now rejected.
                trustManager = TrustManagerUtils.getDefaultTrustManager(null)
                hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
                setEndpointCheckingEnabled(true)
            }
        } else {
            FTPClient()
        }
        client.connectTimeout = 15_000
        try {
            client.connect(host, port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                throw IOException("FTP server refused connection: ${client.replyString?.trim()}")
            }
            if (!client.login(user, password)) {
                throw FtpLoginException(client.replyCode, client.replyString?.trim().orEmpty())
            }
            if (client is FTPSClient) {
                client.execPBSZ(0)
                client.execPROT("P")
            }
            if (passive) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            return client
        } catch (e: Exception) {
            runCatching { if (client.isConnected) client.disconnect() }
            throw e
        }
    }
}

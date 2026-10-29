package com.xerahs.android.core.data.remote.transfer

import java.io.IOException

/** The FTP server refused the login. [replyCode] tells a wrong password (530) from a busy server (421). */
class FtpLoginException(val replyCode: Int, reply: String) : IOException("FTP login failed: $reply")

package com.xerahs.android.core.data.remote.transfer

/**
 * The SSH server at [hostPort] presented a key that doesn't match the one pinned on first
 * connect. Permanent: retrying won't help until the user forgets the saved key.
 */
class HostKeyChangedException(
    val hostPort: String,
    val expected: String,
    val actual: String
) : Exception(
    "The server's identity changed. Expected $expected, got $actual. " +
        "If you changed the server's key, forget the saved key and try again."
)

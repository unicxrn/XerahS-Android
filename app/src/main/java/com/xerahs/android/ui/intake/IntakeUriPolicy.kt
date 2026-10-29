package com.xerahs.android.ui.intake

import android.net.Uri

/** Decides which shared URIs the app is willing to copy. */
object IntakeUriPolicy {

    fun isAllowed(uri: Uri, ownAuthorities: Set<String>): Boolean =
        isAllowed(uri.scheme, uri.authority, ownAuthorities)

    /** Plain-string variant so the rules can be unit tested without Android. */
    fun isAllowed(scheme: String?, authority: String?, ownAuthorities: Set<String>): Boolean {
        if (scheme.isNullOrEmpty() || !scheme.equals("content", ignoreCase = true)) return false
        // Drop user info and port: only the host picks the provider.
        val host = authority.orEmpty().substringAfterLast('@').substringBefore(':')
        return ownAuthorities.none { it.equals(host, ignoreCase = true) }
    }
}

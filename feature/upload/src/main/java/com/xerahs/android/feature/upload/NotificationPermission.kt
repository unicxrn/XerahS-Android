package com.xerahs.android.feature.upload

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Whether the upload screen should ask for POST_NOTIFICATIONS: only on Android 13+, only when it
 * isn't granted yet, and only once ever, so a "no" is never asked again.
 */
internal fun shouldAskNotificationPermission(sdk: Int, granted: Boolean, alreadyAsked: Boolean): Boolean =
    sdk >= 33 && !granted && !alreadyAsked

internal fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

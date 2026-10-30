package com.xerahs.android.core.common.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** How much of the photo library the app can see, for the screenshot watcher. */
enum class ScreenshotAccess { FULL, LIMITED, DENIED }

/** Pure mapping from the granted permissions to an access level, so it can be unit tested. */
fun screenshotAccessFor(sdk: Int, images: Boolean, userSelected: Boolean, legacyRead: Boolean): ScreenshotAccess = when {
    sdk >= 33 && images -> ScreenshotAccess.FULL
    sdk >= 34 && userSelected -> ScreenshotAccess.LIMITED
    sdk < 33 && legacyRead -> ScreenshotAccess.FULL
    else -> ScreenshotAccess.DENIED
}

/** Permissions to ask for when the screenshot watcher is turned on. */
fun screenshotPermissions(sdk: Int = Build.VERSION.SDK_INT): Array<String> = when {
    sdk >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.POST_NOTIFICATIONS,
    )
    sdk >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.POST_NOTIFICATIONS)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun Context.screenshotAccess(): ScreenshotAccess {
    fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val sdk = Build.VERSION.SDK_INT
    return screenshotAccessFor(
        sdk,
        images = sdk >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES),
        userSelected = sdk >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        legacyRead = sdk < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE),
    )
}

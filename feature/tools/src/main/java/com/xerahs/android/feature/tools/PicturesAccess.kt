package com.xerahs.android.feature.tools

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Returns a function that makes sure saving to Pictures/XerahS is allowed, then calls [onResult].
 * On Android 10 and newer, and when the permission is already granted, [onResult] gets true right
 * away. On Android 9 and older it asks for WRITE_EXTERNAL_STORAGE first and passes the answer.
 */
@Composable
fun rememberPicturesWriteAccess(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        callback(granted)
    }
    return {
        if (ToolOutput.needsLegacyWritePermission(context)) launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else callback(true)
    }
}

package com.xerahs.android.feature.history.home

import androidx.compose.ui.graphics.Color
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.ui.lumen.hostColor

/**
 * Small per-destination accent color, used ONLY as a tiny data-dot in the timeline / share card.
 * These are deliberate brand-ish hues and are not themed; everything else flows through
 * [androidx.compose.material3.MaterialTheme].
 */
internal fun UploadDestination.dotColor(): Color = hostColor()

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

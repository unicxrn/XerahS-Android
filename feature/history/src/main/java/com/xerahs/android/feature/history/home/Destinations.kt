package com.xerahs.android.feature.history.home

import androidx.compose.ui.graphics.Color
import com.xerahs.android.core.domain.model.UploadDestination

/**
 * Small per-destination accent color, used ONLY as a tiny data-dot in the timeline / share card.
 * These are deliberate brand-ish hues and are not themed; everything else flows through
 * [androidx.compose.material3.MaterialTheme].
 */
internal fun UploadDestination.dotColor(): Color = when (this) {
    UploadDestination.IMGUR -> Color(0xFF1BB76E)
    UploadDestination.S3 -> Color(0xFFFF9900)
    UploadDestination.FTP -> Color(0xFF2E86DE)
    UploadDestination.SFTP -> Color(0xFF6B7C93)
    UploadDestination.CUSTOM_HTTP -> Color(0xFF8E8E93)
    UploadDestination.LOCAL -> Color(0xFF8E8E93)
}

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

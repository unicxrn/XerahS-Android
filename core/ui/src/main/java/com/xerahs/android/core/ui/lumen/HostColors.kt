package com.xerahs.android.core.ui.lumen

import androidx.compose.ui.graphics.Color
import com.xerahs.android.core.domain.model.UploadDestination

/** Brand-ish host colours, used only as small dots and chip tints. Not themed. */
fun UploadDestination.hostColor(): Color = when (this) {
    UploadDestination.IMGUR -> Color(0xFF1BB76E)
    UploadDestination.S3 -> Color(0xFFFF9900)
    UploadDestination.FTP -> Color(0xFF2E86DE)
    UploadDestination.SFTP -> Color(0xFF6B7C93)
    UploadDestination.CUSTOM_HTTP -> Color(0xFF8E8E93)
    UploadDestination.LOCAL -> Color(0xFF8E8E93)
    UploadDestination.NEXTCLOUD -> Color(0xFF0082C9)
    UploadDestination.IMMICH -> Color(0xFF4250AF)
    UploadDestination.GITHUB_GIST -> Color(0xFF6E7681)
}

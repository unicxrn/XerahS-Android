package com.xerahs.android.core.domain

/** Called after the upload history changes (an upload finished, or items were deleted). */
interface UploadCompletedHook {
    suspend fun onUploadCompleted()
}

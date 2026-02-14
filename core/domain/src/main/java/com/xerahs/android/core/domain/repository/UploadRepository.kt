package com.xerahs.android.core.domain.repository

import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadResult
import java.io.File

interface UploadRepository {
    suspend fun upload(file: File, config: UploadConfig): UploadResult
}

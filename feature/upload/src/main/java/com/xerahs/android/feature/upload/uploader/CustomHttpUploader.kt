package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.data.remote.custom.CustomUploadInput
import com.xerahs.android.core.data.remote.custom.CustomUploadOutcome
import com.xerahs.android.core.data.remote.custom.CustomUploaderClient
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomHttpUploader @Inject constructor(
    private val client: CustomUploaderClient
) {
    suspend fun upload(
        file: File,
        config: UploadConfig.CustomUploaderConfig,
        remoteFileName: String? = null,
        input: String = "",
        inputValues: Map<String, String> = emptyMap(),
    ): UploadResult {
        val fileName = remoteFileName ?: file.name
        return when (val outcome = client.execute(config.spec, CustomUploadInput(file, fileName, input, inputValues))) {
            is CustomUploadOutcome.Success -> UploadResult(
                success = true,
                url = outcome.url,
                deleteUrl = outcome.deletionUrl,
                thumbnailUrl = outcome.thumbnailUrl,
                destination = UploadDestination.CUSTOM_HTTP
            )
            is CustomUploadOutcome.Failure -> UploadResult(
                success = false,
                errorMessage = outcome.message,
                destination = UploadDestination.CUSTOM_HTTP
            )
        }
    }
}

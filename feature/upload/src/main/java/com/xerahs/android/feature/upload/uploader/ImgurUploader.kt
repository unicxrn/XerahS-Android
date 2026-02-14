package com.xerahs.android.feature.upload.uploader

import com.xerahs.android.core.data.remote.imgur.ImgurApi
import com.xerahs.android.core.domain.model.UploadConfig
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.domain.model.UploadResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImgurUploader @Inject constructor(
    private val imgurApi: ImgurApi
) {
    suspend fun upload(file: File, config: UploadConfig.ImgurConfig, remoteFileName: String? = null): UploadResult {
        return try {
            val authorization = if (config.useAnonymous) {
                "Client-ID ${config.clientId}"
            } else {
                "Bearer ${config.accessToken}"
            }

            val requestBody = file.asRequestBody("image/*".toMediaType())
            val multipartBody = MultipartBody.Part.createFormData("image", file.name, requestBody)

            val response = imgurApi.uploadImage(authorization, multipartBody)

            val data = response.data
            if (response.success && data != null) {
                UploadResult(
                    success = true,
                    url = data.link,
                    deleteUrl = data.deletehash?.let {
                        "https://imgur.com/delete/$it"
                    },
                    destination = UploadDestination.IMGUR
                )
            } else {
                UploadResult(
                    success = false,
                    errorMessage = "Imgur upload failed: status ${response.status}",
                    destination = UploadDestination.IMGUR
                )
            }
        } catch (e: Exception) {
            UploadResult(
                success = false,
                errorMessage = "Imgur upload error: ${e.message}",
                destination = UploadDestination.IMGUR
            )
        }
    }

    suspend fun refreshToken(config: UploadConfig.ImgurConfig): UploadConfig.ImgurConfig? {
        return try {
            val refreshToken = config.refreshToken ?: return null
            val response = imgurApi.refreshToken(
                refreshToken = refreshToken,
                clientId = config.clientId,
                clientSecret = config.clientSecret
            )
            config.copy(
                accessToken = response.access_token,
                refreshToken = response.refresh_token ?: refreshToken
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun exchangePin(pin: String, config: UploadConfig.ImgurConfig): UploadConfig.ImgurConfig? {
        return try {
            val response = imgurApi.exchangePin(
                pin = pin,
                clientId = config.clientId,
                clientSecret = config.clientSecret
            )
            config.copy(
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                useAnonymous = false
            )
        } catch (e: Exception) {
            null
        }
    }
}

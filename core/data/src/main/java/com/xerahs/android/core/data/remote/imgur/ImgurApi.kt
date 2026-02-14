package com.xerahs.android.core.data.remote.imgur

import okhttp3.MultipartBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class ImgurResponse(
    val data: ImgurData?,
    val success: Boolean,
    val status: Int
)

data class ImgurData(
    val id: String?,
    val link: String?,
    val deletehash: String?,
    val title: String?,
    val type: String?,
    val width: Int?,
    val height: Int?,
    val size: Int?
)

data class ImgurTokenResponse(
    val access_token: String?,
    val refresh_token: String?,
    val expires_in: Long?,
    val token_type: String?,
    val account_username: String?
)

interface ImgurApi {

    @Multipart
    @POST("3/upload")
    suspend fun uploadImage(
        @Header("Authorization") authorization: String,
        @Part image: MultipartBody.Part
    ): ImgurResponse

    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun refreshToken(
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): ImgurTokenResponse

    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun exchangePin(
        @Field("pin") pin: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "pin"
    ): ImgurTokenResponse
}

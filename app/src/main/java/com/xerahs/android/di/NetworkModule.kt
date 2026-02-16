package com.xerahs.android.di

import com.xerahs.android.core.data.remote.imgur.ImgurApi
import com.xerahs.android.feature.settings.data.GitHubReleaseChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .build()

    @Provides
    @Singleton
    fun provideImgurApi(okHttpClient: OkHttpClient): ImgurApi = Retrofit.Builder()
        .baseUrl("https://api.imgur.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ImgurApi::class.java)

    @Provides
    @Singleton
    fun provideGitHubReleaseChecker(okHttpClient: OkHttpClient): GitHubReleaseChecker =
        GitHubReleaseChecker(okHttpClient)
}

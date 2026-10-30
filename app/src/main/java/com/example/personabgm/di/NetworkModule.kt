package com.example.personabgm.di

import com.example.personabgm.BuildConfig
import com.example.personabgm.data.remote.ManifestApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Tolerant parser: unknown keys are ignored and unknown values fall back to defaults (spec 7.1). */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // Hard cap for the whole call so a stalled host cannot hold the first-launch loading screen.
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        // Retrofit needs a base URL; ManifestApi passes the full manifest URL via @Url.
        val baseUrl = BuildConfig.MANIFEST_URL.toHttpUrl().newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideManifestApi(retrofit: Retrofit): ManifestApi = retrofit.create(ManifestApi::class.java)

    // WeatherApi is added in Phase 4.
}

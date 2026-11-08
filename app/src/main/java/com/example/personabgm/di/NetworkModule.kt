package com.example.personabgm.di

import com.example.personabgm.BuildConfig
import com.example.personabgm.data.remote.ManifestApi
import com.example.personabgm.data.remote.WeatherApi
import com.example.personabgm.security.MusicAccessTokenStore
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
    fun provideOkHttpClient(accessTokenStore: MusicAccessTokenStore): OkHttpClient {
        val configuredApi = BuildConfig.MUSIC_API_BASE_URL.toHttpUrl()
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url
                val trusted = url.scheme == "https" && url.host.equals(configuredApi.host, true) &&
                    url.port == configuredApi.port && url.encodedPath.startsWith("/v1/")
                val token = if (trusted) accessTokenStore.getToken() else null
                val authenticated = if (token != null) request.newBuilder()
                    .header("Authorization", "Bearer $token").build() else request
                chain.proceed(authenticated)
            }
            .build()
    }

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

    @Provides
    @Singleton
    fun provideWeatherApi(retrofit: Retrofit): WeatherApi = retrofit.create(WeatherApi::class.java)
}

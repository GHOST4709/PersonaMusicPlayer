package com.example.personabgm.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** Open-Meteo current weather (spec 5.4). No API key; free for non-commercial use. */
interface WeatherApi {
    @GET("https://api.open-meteo.com/v1/forecast")
    suspend fun currentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String,
        @Query("timezone") timezone: String,
    ): WeatherDto
}

@Serializable
data class WeatherDto(
    val current: CurrentWeatherDto? = null,
)

@Serializable
data class CurrentWeatherDto(
    @SerialName("weather_code") val weatherCode: Int? = null,
)

package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.SchoolScoreDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ForecastApi {

    @GET("schools/{id}/forecast")
    suspend fun getForecast(@Path("id") id: String): ForecastDto

    @GET("forecast/by-location")
    suspend fun getForecastByLocation(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("rockType") rockType: String? = null
    ): ForecastDto

    @GET("forecast/today-scores")
    suspend fun getTodayScores(@Query("ids") ids: List<String>): List<SchoolScoreDto>
}

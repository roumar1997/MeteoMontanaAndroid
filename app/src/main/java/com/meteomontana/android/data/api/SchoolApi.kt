package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.api.dto.SchoolDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interfaz Retrofit: cada método representa un endpoint HTTP.
 * Retrofit genera la implementación automáticamente.
 *
 * `suspend` => función que se ejecuta en una coroutine (sin bloquear el hilo principal).
 */
interface SchoolApi {

    @GET("schools")
    suspend fun getSchools(
        @Query("region")   region: String? = null,
        @Query("style")    style: String? = null,
        @Query("rockType") rockType: List<String>? = null,
        @Query("lat")      lat: Double? = null,
        @Query("lon")      lon: Double? = null,
        @Query("radioKm")  radioKm: Double? = null
    ): List<SchoolDto>

    @GET("schools/{id}")
    suspend fun getSchoolById(@Path("id") id: String): SchoolDto

    @GET("schools/{id}/forecast")
    suspend fun getForecast(@Path("id") id: String): ForecastDto

    @GET("me")
    suspend fun getMyProfile(): PrivateProfileDto
}

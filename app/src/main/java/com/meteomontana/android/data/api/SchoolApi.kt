package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.SchoolDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("schools/search")
    suspend fun searchSchools(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): List<SchoolDto>

    @GET("schools/{id}")
    suspend fun getSchoolById(@Path("id") id: String): SchoolDto
}

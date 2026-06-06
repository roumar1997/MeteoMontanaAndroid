package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.SchoolDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class KtorSchoolApi(private val client: HttpClient) {

    suspend fun getSchools(
        region: String? = null,
        style: String? = null,
        rockType: List<String>? = null,
        lat: Double? = null,
        lon: Double? = null,
        radioKm: Double? = null
    ): List<SchoolDto> = client.get("schools") {
        region?.let { parameter("region", it) }
        style?.let { parameter("style", it) }
        rockType?.forEach { parameter("rockType", it) }
        lat?.let { parameter("lat", it) }
        lon?.let { parameter("lon", it) }
        radioKm?.let { parameter("radioKm", it) }
    }.body()

    suspend fun searchSchools(query: String, limit: Int = 10): List<SchoolDto> =
        client.get("schools/search") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()

    suspend fun getSchoolById(id: String): SchoolDto = client.get("schools/$id").body()
}

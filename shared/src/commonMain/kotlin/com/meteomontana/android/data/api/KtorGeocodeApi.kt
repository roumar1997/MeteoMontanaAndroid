package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.PlaceDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** Geocodificación de localidades (pueblo → coords) vía el backend (/api/geocode). */
class KtorGeocodeApi(private val client: HttpClient) {

    suspend fun geocode(q: String): List<PlaceDto> =
        client.get("geocode") { parameter("q", q) }.body()
}

package com.meteomontana.android.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/**
 * Boletín de montaña oficial de AEMET (vía nuestro backend).
 * Null si la escuela no cae en ninguno de los 9 macizos (HTTP 204).
 */
@Serializable
data class MountainBulletinDto(
    val area: String,
    val areaName: String,
    val day: Int,
    /** nubosidad, pcp, tormentas, temperatura, viento, isocero, iso10, v1500, v3000. */
    val texts: Map<String, String>,
    val spots: List<MountainSpotDto> = emptyList()
)

@Serializable
data class MountainSpotDto(
    val nombre: String,
    val altitud: String = "",
    val minima: Int,
    val maxima: Int
)

class KtorMountainApi(private val client: HttpClient) {

    @Throws(Exception::class)
    suspend fun getBulletin(lat: Double, lon: Double, day: Int = 0): MountainBulletinDto? {
        val resp = client.get("mountain/bulletin") {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("day", day)
        }
        if (resp.status == HttpStatusCode.NoContent) return null
        return resp.body()
    }
}

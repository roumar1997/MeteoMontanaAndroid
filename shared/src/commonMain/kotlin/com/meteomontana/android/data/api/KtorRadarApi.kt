package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.RadarFramesDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Radar de lluvia (datos AEMET cocinados por nuestro backend).
 * La app manda su lat/lon y el backend elige el radar regional más cercano;
 * los frames llegan como PNG transparente ya repintado en paleta Cumbre.
 */
class KtorRadarApi(private val client: HttpClient) {

    /**
     * Sin parámetros: compuesto España, últimas 2h. Con [date] (yyyyMMdd):
     * todos los ciclos de ese día (chips HOY/AYER). Con lat/lon: radar
     * regional más cercano (mejor resolución).
     */
    suspend fun getFrames(
        lat: Double? = null,
        lon: Double? = null,
        hours: Int = 2,
        date: String? = null
    ): RadarFramesDto =
        client.get("radar/frames") {
            lat?.let { parameter("lat", it) }
            lon?.let { parameter("lon", it) }
            date?.let { parameter("date", it) } ?: parameter("hours", hours)
        }.body()

    /** PNG Cumbre del frame (bytes listos para decodificar como imagen). */
    suspend fun getFramePng(radar: String, ts: String): ByteArray =
        client.get("radar/frame/$radar/$ts").body()
}

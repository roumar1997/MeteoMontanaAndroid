package com.meteomontana.android.data.stats

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Llama a Open-Meteo archive (gratis, sin auth) y calcula scores agregados por mes
 * a partir de 3 años de datos diarios. Resultado: lista de 12 enteros 0-100.
 *
 * KMP-friendly: usa kotlinx-serialization + kotlinx-datetime (sin org.json ni Calendar).
 */
class OpenMeteoArchiveClient(private val client: HttpClient = defaultClient()) {

    @Serializable
    data class Response(val daily: Daily? = null)

    @Serializable
    data class Daily(
        val time: List<String> = emptyList(),
        val temperature_2m_max: List<Double?> = emptyList(),
        val precipitation_sum: List<Double?> = emptyList(),
        val wind_speed_10m_max: List<Double?> = emptyList(),
        val relative_humidity_2m_mean: List<Double?> = emptyList()
    )

    suspend fun fetchMonthly(lat: Double, lon: Double, rockType: String?): MonthlyResult {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val endYear = now.year - 1
        val startYear = endYear - 2
        val resp: Response = client.get("https://archive-api.open-meteo.com/v1/archive") {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter("start_date", "$startYear-01-01")
            parameter("end_date", "$endYear-12-31")
            parameter("daily", "temperature_2m_max,precipitation_sum,wind_speed_10m_max,relative_humidity_2m_mean")
            parameter("timezone", "auto")
        }.body()

        val daily = resp.daily ?: return MonthlyResult(List(12) { 0 }, null)

        val perMonth = Array(12) { mutableListOf<Int>() }
        for (i in daily.time.indices) {
            val temp = daily.temperature_2m_max.getOrNull(i) ?: continue
            val rain = daily.precipitation_sum.getOrNull(i) ?: continue
            val wind = daily.wind_speed_10m_max.getOrNull(i) ?: continue
            val hum  = daily.relative_humidity_2m_mean.getOrNull(i) ?: continue
            val prob = if (rain > 0.5) 80 else 10
            val month = daily.time[i].substring(5, 7).toInt() - 1
            perMonth[month] += climbScoreDaily(temp, hum, wind, rain, prob, rockType)
        }
        val avg = perMonth.map { it.takeIf { l -> l.isNotEmpty() }?.average()?.toInt() ?: 0 }
        return MonthlyResult(avg, computeBestRange(avg))
    }

    private fun computeBestRange(scores: List<Int>): String? {
        if (scores.all { it == 0 }) return null
        val names = listOf("Enero","Febrero","Marzo","Abril","Mayo","Junio",
                           "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre")
        var bestAvg = -1.0
        var bestRange: IntRange? = null
        for (len in 2..6) {
            for (start in 0..(12 - len)) {
                val avg = scores.subList(start, start + len).average()
                if (avg > bestAvg) { bestAvg = avg; bestRange = start until (start + len) }
            }
        }
        return bestRange?.let { "${names[it.first]}-${names[it.last]}" }
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true })
            }
        }
    }
}

data class MonthlyResult(val scores: List<Int>, val bestRange: String?)

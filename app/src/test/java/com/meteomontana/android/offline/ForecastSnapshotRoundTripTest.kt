package com.meteomontana.android.offline

import com.meteomontana.android.data.api.dto.ForecastDto
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.dto.toDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip del snapshot offline del forecast:
 *
 *   JSON del backend → DTO → dominio → DTO → JSON guardado → DTO → dominio
 *
 * El modo offline (escuelas guardadas) serializa el forecast del dominio de
 * vuelta a DTO (Mappings.toDto) y lo guarda en SQLDelight. Si alguien añade un
 * campo al dominio y OLVIDA el toDto (o al revés), el snapshot pierde ese dato
 * EN SILENCIO: la app online lo muestra y la offline no — un bug invisible
 * hasta que alguien abre la escuela sin cobertura en la montaña.
 * Este test hace el viaje completo y exige igualdad total.
 */
class ForecastSnapshotRoundTripTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Muestra con TODOS los campos poblados (nada null salvo lo probado aparte). */
    private val backendJson = """
        {
          "schoolId": "esc-1", "schoolName": "Zarzalejo", "lat": 40.54, "lon": -4.11,
          "current": {
            "time": "2026-07-19T10:00", "temperature": 21.5, "humidity": 40.0,
            "windSpeed": 11.0, "precipitation": 0.0, "precipitationProbability": 5,
            "cloudCover": 20, "dewPoint": 7.5, "precip24h": 0.2, "precip72h": 1.4,
            "dryRock": true, "score": 78, "scoreLabel": "Buenas",
            "factors": [
              {"name": "temp", "display": "21°", "passes": true},
              {"name": "rain", "display": "seco", "passes": true}
            ],
            "drying": {"wet": false, "dryingHours": 12, "message": "Seca en ~12 h"}
          },
          "hours": [
            {"time": "2026-07-19T11:00", "temperature": 22.0, "humidity": 38.0,
             "windSpeed": 12.0, "precipitation": 0.0, "precipitationProbability": 5,
             "cloudCover": 15, "dewPoint": 7.0, "score": 80, "scoreLabel": "Buenas",
             "weatherCode": 1}
          ],
          "days": [
            {"date": "2026-07-19", "tempMax": 27.0, "tempMin": 14.0,
             "precipitationTotal": 0.0, "avgScore": 74, "scoreLabel": "Buenas"}
          ],
          "bestDay": {"date": "2026-07-20", "score": 81, "label": "Buenas", "daysFromToday": 1},
          "bestWindow": {"start": "2026-07-19T09:00", "end": "2026-07-19T13:00", "avgScore": 79}
        }
    """.trimIndent()

    @Test
    fun `el viaje completo online-dominio-snapshot-dominio no pierde NADA`() {
        // 1. Lo que llega del backend.
        val dtoOnline = json.decodeFromString<ForecastDto>(backendJson)
        val domainOnline = dtoOnline.toDomain()

        // 2. Lo que el modo offline guarda en disco (dominio → DTO → JSON).
        val snapshotJson = json.encodeToString(ForecastDto.serializer(), domainOnline.toDto())

        // 3. Lo que la app lee del disco sin cobertura.
        val domainOffline = json.decodeFromString<ForecastDto>(snapshotJson).toDomain()

        // Igualdad TOTAL (data classes): cualquier campo perdido rompe aquí.
        assertEquals(domainOnline, domainOffline)
    }

    @Test
    fun `campos anidados criticos sobreviven al snapshot`() {
        val domain = json.decodeFromString<ForecastDto>(backendJson).toDomain()
        val restored = json.decodeFromString<ForecastDto>(
            json.encodeToString(ForecastDto.serializer(), domain.toDto())).toDomain()

        // Los que pintan la pantalla del tiempo (si uno vuelve null/0, la UI
        // offline "funciona" pero miente).
        assertEquals(78, restored.current.score)
        assertEquals(true, restored.current.dryRock)
        assertEquals("Seca en ~12 h", restored.current.drying?.message)
        assertEquals(2, restored.current.factors.size)
        assertEquals(1, restored.hours.first().weatherCode)
        assertEquals(81, restored.bestDay?.score)
        assertEquals("2026-07-19T09:00", restored.bestWindow?.start)
    }

    @Test
    fun `los opcionales null sobreviven como null`() {
        val sinOpcionales = backendJson
            .replace(Regex(",\\s*\"bestDay\": \\{[^}]*\\}"), "")
            .replace(Regex(",\\s*\"bestWindow\": \\{[^}]*\\}"), "")
            .replace(Regex(",\\s*\"drying\": \\{[^}]*\\}"), "")
        val domain = json.decodeFromString<ForecastDto>(sinOpcionales).toDomain()
        val restored = json.decodeFromString<ForecastDto>(
            json.encodeToString(ForecastDto.serializer(), domain.toDto())).toDomain()
        assertEquals(domain, restored)
        assertEquals(null, restored.bestDay)
        assertEquals(null, restored.current.drying)
    }
}

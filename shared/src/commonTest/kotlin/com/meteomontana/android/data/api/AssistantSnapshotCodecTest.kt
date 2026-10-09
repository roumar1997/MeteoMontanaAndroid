package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.AssistantAnswerDto
import com.meteomontana.android.data.api.dto.AssistantSnapshotCodec
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.AssistantClimbing
import com.meteomontana.android.domain.model.AssistantDayWeather
import com.meteomontana.android.domain.model.AssistantHourPoint
import com.meteomontana.android.domain.model.AssistantMessagePayload
import com.meteomontana.android.domain.model.AssistantNow
import com.meteomontana.android.domain.model.AssistantRain
import com.meteomontana.android.domain.model.AssistantWeather
import com.meteomontana.android.domain.model.LineSearchHit
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Las tarjetas del chat se guardan unas horas en el móvil como texto. Lo que se guarda
 * tiene que volver IGUAL: si algo se pierde por el camino, el usuario vería una tarjeta
 * distinta de la que le enseñaron.
 */
class AssistantSnapshotCodecTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val recomendacion = """
        {"status":"OK","recommendation":{"forecastAvailable":true,"dates":["2026-12-05","2026-12-06"],
          "schools":[{"schoolId":"pedriza","name":"La Pedriza","rockType":"Granito","distanceKm":18.2,"lineCount":37,
            "combinedScore":84,"days":[{"date":"2026-12-05","score":84,"rainMm":0.4,"rainProb":5,"rainy":true}],"rainDays":1}]}}
    """.trimIndent()

    private val desglose = """
        {"status":"OK","breakdown":{"school":{"id":"albarracin","name":"Albarracín"},"sectorFilter":"Techos","sun":"SHADE",
          "dayPart":"AFTERNOON","day":"2026-10-08",
          "sectors":[{"sectorId":"z1","name":"Techos","total":5,"matching":2,"unknownOrientation":1,
            "stones":[{"blockId":"s1","name":"Piedra 1","aspect":"N","sun":"SHADE","lineCount":3,"firstLineId":"s1-l0",
              "photoPath":"fotos/s1.jpg","linePath":"[{\"x\":0.3,\"y\":0.4}]","grade":"7A","lineName":"La lágrima"}],
            "unknownStones":[{"blockId":"s9","name":"Piedra 9","lineCount":1}]}],
          "totalMatching":2,"totalStones":5,"stonesWithOrientation":4}}
    """.trimIndent()

    private val hit = LineSearchHit(
        schoolId = "albarracin", schoolName = "Albarracín", blockId = "b1", blockName = "Piedra 1",
        lineId = "l1", lineName = "La lágrima", grade = "6C", sectorName = "Techos",
        photoPath = "fotos/b1.jpg", linePath = "[{\"x\":0.1,\"y\":0.2}]", startType = "SIT",
        lat = 40.38, lon = -1.4, orientation = "N", discipline = "BOULDER",
        rating = 4.5, ratingCount = 8
    )

    @Test fun laRecomendacionVuelveIgual() {
        val original = json.decodeFromString<AssistantAnswerDto>(recomendacion).toDomain().recommendation!!
        val payload = AssistantMessagePayload(original, null, emptyList(), 0)
        val vuelta = AssistantSnapshotCodec.decode(AssistantSnapshotCodec.encode(payload))
        assertEquals(original, vuelta.recommendation)
        assertNull(vuelta.breakdown)
    }

    @Test fun laPiedraConservaLaFotoElTrazoYElGradoDeSuPrimeraVia() {
        val original = json.decodeFromString<AssistantAnswerDto>(desglose).toDomain().breakdown!!
        val vuelta = AssistantSnapshotCodec.decode(
            AssistantSnapshotCodec.encode(AssistantMessagePayload(null, original, emptyList(), 0))).breakdown!!

        val piedra = vuelta.sectors[0].stones[0]
        assertEquals("fotos/s1.jpg", piedra.photoPath)
        assertEquals("7A", piedra.grade)
        assertEquals("La lágrima", piedra.lineName)
        assertEquals("[{\"x\":0.3,\"y\":0.4}]", piedra.linePath)
        assertNull(vuelta.sectors[0].unknownStones[0].photoPath)      // sin foto en el JSON: no se inventa
    }

    @Test fun elDesgloseConserva_lasPiedrasSinOrientacionYLasVias() {
        val original = json.decodeFromString<AssistantAnswerDto>(desglose).toDomain().breakdown!!
        val payload = AssistantMessagePayload(null, original, emptyList(), 0)
        val vuelta = AssistantSnapshotCodec.decode(AssistantSnapshotCodec.encode(payload)).breakdown!!
        assertEquals(original, vuelta)
        assertEquals("s1-l0", vuelta.sectors.single().stones.single().firstLineId)
        assertNull(vuelta.sectors.single().unknownStones.single().aspect)
    }

    @Test fun losResultadosDeBusquedaVuelvenConSuTotal() {
        val payload = AssistantMessagePayload(null, null, listOf(hit), 42)
        val vuelta = AssistantSnapshotCodec.decode(AssistantSnapshotCodec.encode(payload))
        assertEquals(listOf(hit), vuelta.hits)
        assertEquals(42, vuelta.hitsTotal)
        assertEquals(4.5, vuelta.hits.single().rating)        // las estrellas también se guardan
        assertEquals(8, vuelta.hits.single().ratingCount)
    }

    @Test fun elTiempoVuelveIgual() {
        val tiempo = AssistantWeather(
            placeName = null, myLocation = true, topic = "RAIN",
            now = AssistantNow(14.2, 71.0, 18.5, 0.0, 20, 60, 8.1),
            hours = listOf(AssistantHourPoint("2026-10-08T15:00", 14.0, 0.0, 10, 16.0),
                AssistantHourPoint("2026-10-08T16:00", 13.0, 1.2, 80, 22.0)),
            rain = AssistantRain(true, 1, 1.2, 80, 6, "2026-10-08T16:00", "2026-10-08T21:00"),
            climbing = AssistantClimbing(62, "Aceptable", true, "Seca en ~12 h", "2026-10-09T10:00", "2026-10-09T13:00"),
            days = listOf(
                AssistantDayWeather("2026-10-09", 5.0, 14.0, 0.0, 60, "Bueno"),
                AssistantDayWeather("2026-10-10", 6.5, 15.0, 1.2, 48, null)
            )
        )
        val vuelta = AssistantSnapshotCodec.decode(
            AssistantSnapshotCodec.encode(AssistantMessagePayload(null, null, emptyList(), 0, tiempo)))
        assertEquals(tiempo, vuelta.weather)
        assertEquals("2026-10-08T16:00", vuelta.weather!!.rain.startsAt)      // la hora de inicio de la lluvia se guarda
        assertEquals(2, vuelta.weather!!.days.size)                           // y la mínima/máxima de cada día pedido
        assertEquals(6.5, vuelta.weather!!.days[1].tempMin)
    }

    @Test fun unTextoRotoLanzaEnVezDeInventar() {
        var lanzo = false
        try { AssistantSnapshotCodec.decode("esto no es json") } catch (e: Exception) { lanzo = true }
        assertEquals(true, lanzo)
    }
}

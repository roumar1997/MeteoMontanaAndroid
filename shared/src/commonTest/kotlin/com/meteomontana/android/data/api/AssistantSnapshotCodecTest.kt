package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.AssistantAnswerDto
import com.meteomontana.android.data.api.dto.AssistantSnapshotCodec
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.AssistantMessagePayload
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
            "stones":[{"blockId":"s1","name":"Piedra 1","aspect":"N","sun":"SHADE","lineCount":3,"firstLineId":"s1-l0"}],
            "unknownStones":[{"blockId":"s9","name":"Piedra 9","lineCount":1}]}],
          "totalMatching":2,"totalStones":5,"stonesWithOrientation":4}}
    """.trimIndent()

    private val hit = LineSearchHit(
        schoolId = "albarracin", schoolName = "Albarracín", blockId = "b1", blockName = "Piedra 1",
        lineId = "l1", lineName = "La lágrima", grade = "6C", sectorName = "Techos",
        photoPath = "fotos/b1.jpg", linePath = "[{\"x\":0.1,\"y\":0.2}]", startType = "SIT",
        lat = 40.38, lon = -1.4, orientation = "N", discipline = "BOULDER"
    )

    @Test fun laRecomendacionVuelveIgual() {
        val original = json.decodeFromString<AssistantAnswerDto>(recomendacion).toDomain().recommendation!!
        val payload = AssistantMessagePayload(original, null, emptyList(), 0)
        val vuelta = AssistantSnapshotCodec.decode(AssistantSnapshotCodec.encode(payload))
        assertEquals(original, vuelta.recommendation)
        assertNull(vuelta.breakdown)
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
    }

    @Test fun unTextoRotoLanzaEnVezDeInventar() {
        var lanzo = false
        try { AssistantSnapshotCodec.decode("esto no es json") } catch (e: Exception) { lanzo = true }
        assertEquals(true, lanzo)
    }
}

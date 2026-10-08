package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.AssistantAnswerDto
import com.meteomontana.android.data.api.dto.AssistantAskRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.data.api.dto.toDto
import com.meteomontana.android.domain.model.AssistantStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contrato POST /api/assistant/ask entre backend y apps. Las muestras tienen la
 * forma EXACTA que serializa el backend (AskAssistantUseCase.Answer): si el
 * backend cambia un nombre de campo, este test (y su gemelo en iOS) debe verse
 * romper antes que la app instalada.
 */
class AssistantContractTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val recomendacion = """
        {"status":"OK",
         "understood":{"intent":"RECOMMEND","dateFrom":"2026-12-05","dateTo":"2026-12-08","gradeMin":"6A","gradeMax":"7A",
           "discipline":"BOULDER","rockTypes":[],"orientations":[],"maxDistanceKm":null,"q":null,
           "schoolMention":null,"sectorMention":null,"sun":null,"dayPart":null},
         "resolved":{"school":null,"sector":null},
         "recommendation":{"forecastAvailable":true,"dates":["2026-12-05","2026-12-06"],
           "schools":[{"schoolId":"pedriza","name":"La Pedriza","rockType":"Granito","distanceKm":18.2,"lineCount":37,
             "combinedScore":84,"days":[{"date":"2026-12-05","score":84,"rainMm":0.0,"rainProb":5,"rainy":false}],"rainDays":0}]},
         "breakdown":null,"clarification":null,"campoNuevoDelFuturo":123}
    """.trimIndent()

    private val desglose = """
        {"status":"OK","understood":{"intent":"BREAKDOWN","rockTypes":[],"orientations":[],"schoolMention":"Albarracín","sun":"SHADE","dayPart":"AFTERNOON"},
         "resolved":{"school":{"id":"albarracin","name":"Albarracín"},"sector":null},"recommendation":null,
         "breakdown":{"school":{"id":"albarracin","name":"Albarracín"},"sectorFilter":null,"sun":"SHADE","dayPart":"AFTERNOON","day":"2026-10-08",
           "sectors":[{"sectorId":"z1","name":"Techos","total":5,"matching":2,"unknownOrientation":1,
             "stones":[{"blockId":"s1","name":"Piedra 1","aspect":"N","sun":"SHADE","lineCount":3,"firstLineId":"s1-l0"}],
             "unknownStones":[{"blockId":"s9","name":"Piedra 9","aspect":null,"sun":null,"lineCount":1}]}],
           "totalMatching":2,"totalStones":5,"stonesWithOrientation":4},"clarification":null}
    """.trimIndent()

    private val tiempo = """
        {"status":"OK","understood":{"intent":"WEATHER","rockTypes":[],"orientations":[],"schoolMentions":[],
           "topRated":false,"hoursAhead":3,"weatherTopic":"RAIN","useMyLocation":true},
         "resolved":{"school":null,"sector":null},"recommendation":null,"breakdown":null,"clarification":null,
         "weather":{"placeName":null,"myLocation":true,"topic":"RAIN",
           "now":{"temperature":14.2,"humidity":71.0,"windKmh":18.5,"precipitationMm":0.0,"rainProbability":20,"cloudCover":60,"dewPoint":null},
           "hours":[{"time":"2026-10-08T15:00","temperature":14.0,"precipitationMm":0.0,"rainProbability":10,"windKmh":16.0}],
           "rain":{"expected":true,"startsInHours":2,"totalMm":1.4,"maxProbability":85,"hoursChecked":3,"startsAt":"2026-10-08T17:00","until":"2026-10-08T19:00"},
           "climbing":{"score":62,"label":"Aceptable","rockWet":false,"dryingMessage":null,"bestWindowStart":null,"bestWindowEnd":null}}}
    """.trimIndent()

    private val pideEscuela = """
        {"status":"NEEDS_INPUT","understood":{"intent":"BREAKDOWN","rockTypes":[],"orientations":[]},"resolved":null,"recommendation":null,"breakdown":null,
         "clarification":{"kind":"SCHOOL_AMBIGUOUS","options":[{"id":"a","name":"Torrelodones"},{"id":"b","name":"Torrelaguna"}]}}
    """.trimIndent()

    @Test fun recomendacionSeLeeConSusDias() {
        val a = json.decodeFromString<AssistantAnswerDto>(recomendacion).toDomain()
        assertEquals(AssistantStatus.OK, a.status)
        val s = a.recommendation!!.schools.single()
        assertEquals("La Pedriza", s.name)
        assertEquals(37, s.lineCount)
        assertEquals(84, s.combinedScore)
        assertEquals(1, s.days.size)
        assertEquals("BOULDER", a.understood!!.discipline)
        assertNull(a.breakdown)
    }

    @Test fun desgloseDiferenciaConfirmadasDeSinOrientacion() {
        val a = json.decodeFromString<AssistantAnswerDto>(desglose).toDomain()
        val sector = a.breakdown!!.sectors.single()
        assertEquals("Techos", sector.name)
        assertEquals(2, sector.matching)
        assertEquals("N", sector.stones.single().aspect)
        assertEquals("s1-l0", sector.stones.single().firstLineId)   // para abrir la piedra desde el chat
        assertNull(sector.unknownStones.single().firstLineId)        // una piedra sin vías no se puede abrir así
        assertNull(sector.unknownStones.single().aspect)   // sin orientación votada: se enseña igualmente
        assertEquals(4, a.breakdown!!.stonesWithOrientation)
        assertEquals("albarracin", a.resolvedSchool!!.id)
    }

    @Test fun clarificacionTraeLasOpciones() {
        val a = json.decodeFromString<AssistantAnswerDto>(pideEscuela).toDomain()
        assertEquals(AssistantStatus.NEEDS_INPUT, a.status)
        assertEquals("SCHOOL_AMBIGUOUS", a.clarification!!.kind)
        assertEquals(listOf("Torrelodones", "Torrelaguna"), a.clarification!!.options.map { it.name })
    }

    @Test fun elTiempoSeLeeConLaLluviaYLaUbicacion() {
        val a = json.decodeFromString<AssistantAnswerDto>(tiempo).toDomain()
        val w = a.weather!!
        assertTrue(w.myLocation)
        assertNull(w.placeName)                              // null = "tu ubicación"
        assertEquals("RAIN", w.topic)
        assertEquals(2, w.rain.startsInHours)
        assertEquals(85, w.rain.maxProbability)
        assertEquals("2026-10-08T17:00", w.rain.startsAt)
        assertEquals("2026-10-08T19:00", w.rain.until)
        assertEquals(18.5, w.now.windKmh)
        assertEquals(62, w.climbing.score)
        assertEquals(3, a.understood!!.hoursAhead)
        assertTrue(a.understood!!.useMyLocation)
    }

    @Test fun unaRespuestaAntiguaSinTiempoSigueLeyendose() {
        val a = json.decodeFromString<AssistantAnswerDto>(recomendacion).toDomain()
        assertNull(a.weather)
        assertTrue(a.understood!!.schoolMentions.isEmpty())  // campos nuevos ausentes → valores por defecto
        assertEquals(false, a.understood!!.topRated)
    }

    @Test fun unEstadoDesconocidoSeTrataComoNoDisponible() {
        val a = json.decodeFromString<AssistantAnswerDto>("""{"status":"ESTADO_DEL_FUTURO"}""").toDomain()
        assertEquals(AssistantStatus.UNAVAILABLE, a.status)
    }

    @Test fun laPeticionNoManda_loQueNoHayQueMandar() {
        val body = json.encodeToString(AssistantAskRequest(text = "bloques de 7a"))
        assertFalse(body.contains("previous"))
        assertTrue(body.contains("\"text\":\"bloques de 7a\""))
    }

    @Test fun loEntendidoSeDevuelveTalCualEnElMensajeSiguiente() {
        val entendido = json.decodeFromString<AssistantAnswerDto>(desglose).toDomain().understood!!
        val body = json.encodeToString(AssistantAskRequest("ahora solo 6a", entendido.toDto()))
        assertTrue(body.contains("\"schoolMention\":\"Albarracín\""))
        assertTrue(body.contains("\"sun\":\"SHADE\""))
    }
}

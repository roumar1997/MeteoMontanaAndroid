package com.meteomontana.android.data.api.dto

import com.meteomontana.android.data.api.LineSearchHitDto
import com.meteomontana.android.domain.model.AssistantBreakdown
import com.meteomontana.android.domain.model.AssistantMessagePayload
import com.meteomontana.android.domain.model.AssistantOption
import com.meteomontana.android.domain.model.AssistantRecommendation
import com.meteomontana.android.domain.model.LineSearchHit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Convierte las tarjetas de un mensaje del asistente (escuelas recomendadas,
 * desglose por sectores, vías/bloques encontrados) a texto y de vuelta, para que
 * iOS pueda guardarlas unas horas en el móvil. Vive aquí, en Kotlin, para
 * reutilizar los DTO de arriba y poder probarlo con tests en vez de duplicar el
 * formato a mano en Swift.
 */
@Serializable
data class AssistantMessagePayloadDto(
    val recommendation: AssistantRecommendationDto? = null,
    val breakdown: AssistantBreakdownDto? = null,
    val hits: List<LineSearchHitDto> = emptyList(),
    val hitsTotal: Int = 0,
    val weather: AssistantWeatherDto? = null
)

object AssistantSnapshotCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Throws(Exception::class)
    fun encode(payload: AssistantMessagePayload): String =
        json.encodeToString(payload.toDto())

    @Throws(Exception::class)
    fun decode(text: String): AssistantMessagePayload =
        json.decodeFromString<AssistantMessagePayloadDto>(text).toDomain()
}

// ── dominio → DTO (el sentido contrario al de AssistantDto.kt) ──────────────

private fun AssistantMessagePayload.toDto() = AssistantMessagePayloadDto(
    recommendation = recommendation?.toDto(),
    breakdown = breakdown?.toDto(),
    hits = hits.map { it.toDto() },
    hitsTotal = hitsTotal,
    weather = weather?.toDto()
)

private fun AssistantOption.toDto() = AssistantOptionDto(id, name)

private fun AssistantRecommendation.toDto() = AssistantRecommendationDto(
    forecastAvailable = forecastAvailable,
    byCount = byCount,
    filtered = filtered,
    dates = dates,
    schools = schools.map { s ->
        AssistantSchoolCardDto(
            s.schoolId, s.name, s.rockType, s.distanceKm, s.lineCount, s.combinedScore,
            s.days.map { AssistantDayDto(it.date, it.score, it.rainMm, it.rainProb, it.rainy) },
            s.rainDays
        )
    }
)

private fun AssistantBreakdown.toDto() = AssistantBreakdownDto(
    school = school.toDto(),
    sectorFilter = sectorFilter,
    sun = sun,
    dayPart = dayPart,
    day = day,
    sectors = sectors.map { s ->
        AssistantSectorDto(
            s.sectorId, s.name, s.total, s.matching, s.unknownOrientation,
            s.stones.map { AssistantStoneDto(it.blockId, it.name, it.aspect, it.sun, it.lineCount, it.firstLineId) },
            s.unknownStones.map { AssistantStoneDto(it.blockId, it.name, it.aspect, it.sun, it.lineCount, it.firstLineId) }
        )
    },
    totalMatching = totalMatching,
    totalStones = totalStones,
    stonesWithOrientation = stonesWithOrientation
)

private fun LineSearchHit.toDto() = LineSearchHitDto(
    schoolId, schoolName, blockId, blockName, lineId, lineName, grade, sectorName,
    photoPath, linePath, startType, lat, lon, orientation, discipline, rating, ratingCount
)

// ── DTO → dominio ───────────────────────────────────────────────────────────

private fun AssistantMessagePayloadDto.toDomain() = AssistantMessagePayload(
    recommendation = recommendation?.let { r ->
        // Se reutiliza la conversión de la respuesta completa para no duplicarla.
        AssistantAnswerDto(status = "OK", recommendation = r).toDomain().recommendation
    },
    breakdown = breakdown?.let { b ->
        AssistantAnswerDto(status = "OK", breakdown = b).toDomain().breakdown
    },
    hits = hits.map { h ->
        LineSearchHit(
            h.schoolId, h.schoolName, h.blockId, h.blockName, h.lineId, h.lineName, h.grade,
            h.sectorName, h.photoPath, h.linePath, h.startType, h.lat, h.lon, h.orientation, h.discipline,
            h.rating, h.ratingCount
        )
    },
    hitsTotal = hitsTotal,
    weather = weather?.toDomain()
)

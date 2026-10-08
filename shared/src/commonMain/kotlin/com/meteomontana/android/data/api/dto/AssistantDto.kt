package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.AssistantAnswer
import com.meteomontana.android.domain.model.AssistantBreakdown
import com.meteomontana.android.domain.model.AssistantClarification
import com.meteomontana.android.domain.model.AssistantDay
import com.meteomontana.android.domain.model.AssistantOption
import com.meteomontana.android.domain.model.AssistantRecommendation
import com.meteomontana.android.domain.model.AssistantSchoolCard
import com.meteomontana.android.domain.model.AssistantSector
import com.meteomontana.android.domain.model.AssistantStatus
import com.meteomontana.android.domain.model.AssistantStone
import com.meteomontana.android.domain.model.AssistantUnderstood
import kotlinx.serialization.Serializable

/**
 * Contrato de POST /api/assistant/ask (espejo de `AskAssistantUseCase.Answer`
 * del backend). Todo lo que el servidor pueda añadir en el futuro llega como
 * campo nuevo y se ignora (ignoreUnknownKeys); todo lo opcional tiene valor por
 * defecto para no romper si falta.
 */
@Serializable
data class AssistantAskRequest(
    val text: String,
    val previous: AssistantUnderstoodDto? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Escuela que el usuario tiene abierta, si la hay. */
    val schoolId: String? = null
)

@Serializable
data class AssistantUnderstoodDto(
    val intent: String,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val gradeMin: String? = null,
    val gradeMax: String? = null,
    val discipline: String? = null,
    val rockTypes: List<String> = emptyList(),
    val orientations: List<String> = emptyList(),
    val maxDistanceKm: Double? = null,
    val q: String? = null,
    val schoolMention: String? = null,
    val sectorMention: String? = null,
    val sun: String? = null,
    val dayPart: String? = null
)

@Serializable
data class AssistantOptionDto(val id: String, val name: String)

@Serializable
data class AssistantResolvedDto(
    val school: AssistantOptionDto? = null,
    val sector: AssistantOptionDto? = null
)

@Serializable
data class AssistantDayDto(
    val date: String,
    val score: Int = 0,
    val rainMm: Double = 0.0,
    val rainProb: Int = 0,
    val rainy: Boolean = false
)

@Serializable
data class AssistantSchoolCardDto(
    val schoolId: String,
    val name: String,
    val rockType: String? = null,
    val distanceKm: Double? = null,
    val lineCount: Int = 0,
    val combinedScore: Int? = null,
    val days: List<AssistantDayDto> = emptyList(),
    val rainDays: Int = 0
)

@Serializable
data class AssistantRecommendationDto(
    val forecastAvailable: Boolean = true,
    val dates: List<String> = emptyList(),
    val schools: List<AssistantSchoolCardDto> = emptyList()
)

@Serializable
data class AssistantStoneDto(
    val blockId: String,
    val name: String,
    val aspect: String? = null,
    val sun: String? = null,
    val lineCount: Int = 0,
    val firstLineId: String? = null
)

@Serializable
data class AssistantSectorDto(
    val sectorId: String? = null,
    val name: String? = null,
    val total: Int = 0,
    val matching: Int = 0,
    val unknownOrientation: Int = 0,
    val stones: List<AssistantStoneDto> = emptyList(),
    val unknownStones: List<AssistantStoneDto> = emptyList()
)

@Serializable
data class AssistantBreakdownDto(
    val school: AssistantOptionDto,
    val sectorFilter: String? = null,
    val sun: String? = null,
    val dayPart: String? = null,
    val day: String,
    val sectors: List<AssistantSectorDto> = emptyList(),
    val totalMatching: Int = 0,
    val totalStones: Int = 0,
    val stonesWithOrientation: Int = 0
)

@Serializable
data class AssistantClarificationDto(
    val kind: String,
    val options: List<AssistantOptionDto> = emptyList()
)

@Serializable
data class AssistantAnswerDto(
    val status: String,
    val understood: AssistantUnderstoodDto? = null,
    val resolved: AssistantResolvedDto? = null,
    val recommendation: AssistantRecommendationDto? = null,
    val breakdown: AssistantBreakdownDto? = null,
    val clarification: AssistantClarificationDto? = null
)

// ── DTO → dominio ──────────────────────────────────────────────────────────

fun AssistantUnderstood.toDto() = AssistantUnderstoodDto(
    intent, dateFrom, dateTo, gradeMin, gradeMax, discipline, rockTypes, orientations,
    maxDistanceKm, q, schoolMention, sectorMention, sun, dayPart
)

private fun AssistantUnderstoodDto.toDomain() = AssistantUnderstood(
    intent, dateFrom, dateTo, gradeMin, gradeMax, discipline, rockTypes, orientations,
    maxDistanceKm, q, schoolMention, sectorMention, sun, dayPart
)

private fun AssistantOptionDto.toDomain() = AssistantOption(id, name)

private fun AssistantStoneDto.toDomain() = AssistantStone(blockId, name, aspect, sun, lineCount, firstLineId)

/** Un estado desconocido (servidor más nuevo que la app) se trata como "no disponible". */
private fun String.toAssistantStatus(): AssistantStatus =
    AssistantStatus.entries.firstOrNull { it.name == this } ?: AssistantStatus.UNAVAILABLE

fun AssistantAnswerDto.toDomain() = AssistantAnswer(
    status = status.toAssistantStatus(),
    understood = understood?.toDomain(),
    resolvedSchool = resolved?.school?.toDomain(),
    resolvedSector = resolved?.sector?.toDomain(),
    recommendation = recommendation?.let { r ->
        AssistantRecommendation(
            forecastAvailable = r.forecastAvailable,
            dates = r.dates,
            schools = r.schools.map { s ->
                AssistantSchoolCard(
                    s.schoolId, s.name, s.rockType, s.distanceKm, s.lineCount, s.combinedScore,
                    s.days.map { AssistantDay(it.date, it.score, it.rainMm, it.rainProb, it.rainy) },
                    s.rainDays
                )
            }
        )
    },
    breakdown = breakdown?.let { b ->
        AssistantBreakdown(
            school = b.school.toDomain(),
            sectorFilter = b.sectorFilter,
            sun = b.sun,
            dayPart = b.dayPart,
            day = b.day,
            sectors = b.sectors.map { s ->
                AssistantSector(
                    s.sectorId, s.name, s.total, s.matching, s.unknownOrientation,
                    s.stones.map { it.toDomain() }, s.unknownStones.map { it.toDomain() }
                )
            },
            totalMatching = b.totalMatching,
            totalStones = b.totalStones,
            stonesWithOrientation = b.stonesWithOrientation
        )
    },
    clarification = clarification?.let { c ->
        AssistantClarification(c.kind, c.options.map { it.toDomain() })
    }
)

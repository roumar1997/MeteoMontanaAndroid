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
import com.meteomontana.android.domain.model.AssistantClimbing
import com.meteomontana.android.domain.model.AssistantHourPoint
import com.meteomontana.android.domain.model.AssistantNow
import com.meteomontana.android.domain.model.AssistantRain
import com.meteomontana.android.domain.model.AssistantUnderstood
import com.meteomontana.android.domain.model.AssistantDayWeather
import com.meteomontana.android.domain.model.AssistantGradeBand
import com.meteomontana.android.domain.model.AssistantSummary
import com.meteomontana.android.domain.model.AssistantWeather
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
    val dayPart: String? = null,
    val schoolMentions: List<String> = emptyList(),
    val topRated: Boolean = false,
    val minStars: Int? = null,
    val hoursAhead: Int? = null,
    val weatherTopic: String? = null,
    val useMyLocation: Boolean = false,
    val beta: String? = null,
    val startType: String? = null,
    val withTopo: Boolean = false,
    val noRain: Boolean = false,
    val mineTopic: String? = null,
    val year: Int? = null,
    val notDone: Boolean = false,
    val action: String? = null,
    val mostLines: Boolean = false
)

@Serializable
data class AssistantOptionDto(val id: String, val name: String)

@Serializable
data class AssistantResolvedDto(
    val school: AssistantOptionDto? = null,
    val sector: AssistantOptionDto? = null,
    val schools: List<AssistantOptionDto> = emptyList()
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
    val schools: List<AssistantSchoolCardDto> = emptyList(),
    val byCount: Boolean = false,
    val filtered: Boolean = false
)

@Serializable
data class AssistantStoneDto(
    val blockId: String,
    val name: String,
    val aspect: String? = null,
    val sun: String? = null,
    val lineCount: Int = 0,
    val firstLineId: String? = null,
    val photoPath: String? = null,
    val linePath: String? = null,
    val grade: String? = null,
    val lineName: String? = null
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
data class AssistantNowDto(
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val windKmh: Double = 0.0,
    val precipitationMm: Double = 0.0,
    val rainProbability: Int = 0,
    val cloudCover: Int = 0,
    val dewPoint: Double? = null
)

@Serializable
data class AssistantHourPointDto(
    val time: String,
    val temperature: Double = 0.0,
    val precipitationMm: Double = 0.0,
    val rainProbability: Int = 0,
    val windKmh: Double = 0.0
)

@Serializable
data class AssistantRainDto(
    val expected: Boolean = false,
    val startsInHours: Int? = null,
    val totalMm: Double = 0.0,
    val maxProbability: Int = 0,
    val hoursChecked: Int = 0,
    val startsAt: String? = null,
    val until: String? = null
)

@Serializable
data class AssistantClimbingDto(
    val score: Int = 0,
    val label: String = "",
    val rockWet: Boolean = false,
    val dryingMessage: String? = null,
    val bestWindowStart: String? = null,
    val bestWindowEnd: String? = null
)

@Serializable
data class AssistantWeatherDto(
    val placeName: String? = null,
    val myLocation: Boolean = false,
    val topic: String = "GENERAL",
    val now: AssistantNowDto = AssistantNowDto(),
    val hours: List<AssistantHourPointDto> = emptyList(),
    val rain: AssistantRainDto = AssistantRainDto(),
    val climbing: AssistantClimbingDto = AssistantClimbingDto(),
    val days: List<AssistantDayWeatherDto> = emptyList()
)

@Serializable
data class AssistantDayWeatherDto(
    val date: String,
    val tempMin: Double = 0.0,
    val tempMax: Double = 0.0,
    val precipitationMm: Double = 0.0,
    val score: Int = 0,
    val scoreLabel: String? = null
)

@Serializable
data class AssistantAnswerDto(
    val status: String,
    val understood: AssistantUnderstoodDto? = null,
    val resolved: AssistantResolvedDto? = null,
    val recommendation: AssistantRecommendationDto? = null,
    val breakdown: AssistantBreakdownDto? = null,
    val clarification: AssistantClarificationDto? = null,
    val weather: AssistantWeatherDto? = null,
    val summary: AssistantSummaryDto? = null
)

@Serializable
data class AssistantGradeBandDto(val band: String, val count: Int = 0)

@Serializable
data class AssistantSummaryDto(
    val school: AssistantOptionDto,
    val rockType: String? = null,
    val region: String? = null,
    val stones: Int = 0,
    val sectors: Int = 0,
    val lines: Int = 0,
    val boulderLines: Int = 0,
    val routeLines: Int = 0,
    val hardest: String? = null,
    val grades: List<AssistantGradeBandDto> = emptyList(),
    val bestMonths: List<Int> = emptyList()
)

// ── DTO → dominio ──────────────────────────────────────────────────────────

fun AssistantUnderstood.toDto() = AssistantUnderstoodDto(
    intent, dateFrom, dateTo, gradeMin, gradeMax, discipline, rockTypes, orientations,
    maxDistanceKm, q, schoolMention, sectorMention, sun, dayPart,
    schoolMentions, topRated, minStars, hoursAhead, weatherTopic, useMyLocation, beta, startType, withTopo, noRain, mineTopic, year, notDone, action, mostLines
)

private fun AssistantUnderstoodDto.toDomain() = AssistantUnderstood(
    intent, dateFrom, dateTo, gradeMin, gradeMax, discipline, rockTypes, orientations,
    maxDistanceKm, q, schoolMention, sectorMention, sun, dayPart,
    schoolMentions, topRated, minStars, hoursAhead, weatherTopic, useMyLocation, beta, startType, withTopo, noRain, mineTopic, year, notDone, action, mostLines
)

internal fun AssistantWeatherDto.toDomain() = AssistantWeather(
    placeName = placeName, myLocation = myLocation, topic = topic,
    now = AssistantNow(now.temperature, now.humidity, now.windKmh, now.precipitationMm,
        now.rainProbability, now.cloudCover, now.dewPoint),
    hours = hours.map { AssistantHourPoint(it.time, it.temperature, it.precipitationMm, it.rainProbability, it.windKmh) },
    rain = AssistantRain(rain.expected, rain.startsInHours, rain.totalMm, rain.maxProbability, rain.hoursChecked,
        rain.startsAt, rain.until),
    climbing = AssistantClimbing(climbing.score, climbing.label, climbing.rockWet, climbing.dryingMessage,
        climbing.bestWindowStart, climbing.bestWindowEnd),
    days = days.map { AssistantDayWeather(it.date, it.tempMin, it.tempMax, it.precipitationMm, it.score, it.scoreLabel) }
)

internal fun AssistantWeather.toDto() = AssistantWeatherDto(
    placeName = placeName, myLocation = myLocation, topic = topic,
    now = AssistantNowDto(now.temperature, now.humidity, now.windKmh, now.precipitationMm,
        now.rainProbability, now.cloudCover, now.dewPoint),
    hours = hours.map { AssistantHourPointDto(it.time, it.temperature, it.precipitationMm, it.rainProbability, it.windKmh) },
    rain = AssistantRainDto(rain.expected, rain.startsInHours, rain.totalMm, rain.maxProbability, rain.hoursChecked,
        rain.startsAt, rain.until),
    climbing = AssistantClimbingDto(climbing.score, climbing.label, climbing.rockWet, climbing.dryingMessage,
        climbing.bestWindowStart, climbing.bestWindowEnd),
    days = days.map { AssistantDayWeatherDto(it.date, it.tempMin, it.tempMax, it.precipitationMm, it.score, it.scoreLabel) }
)

private fun AssistantOptionDto.toDomain() = AssistantOption(id, name)

private fun AssistantStoneDto.toDomain() =
    AssistantStone(blockId, name, aspect, sun, lineCount, firstLineId, photoPath, linePath, grade, lineName)

/** Un estado desconocido (servidor más nuevo que la app) se trata como "no disponible". */
private fun String.toAssistantStatus(): AssistantStatus =
    AssistantStatus.entries.firstOrNull { it.name == this } ?: AssistantStatus.UNAVAILABLE

fun AssistantAnswerDto.toDomain() = AssistantAnswer(
    status = status.toAssistantStatus(),
    understood = understood?.toDomain(),
    resolvedSchool = resolved?.school?.toDomain(),
    resolvedSector = resolved?.sector?.toDomain(),
    resolvedSchools = resolved?.schools.orEmpty().map { it.toDomain() },
    recommendation = recommendation?.let { r ->
        AssistantRecommendation(
            forecastAvailable = r.forecastAvailable,
            dates = r.dates,
            byCount = r.byCount,
            filtered = r.filtered,
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
    },
    weather = weather?.toDomain(),
    summary = summary?.let { s ->
        AssistantSummary(
            AssistantOption(s.school.id, s.school.name), s.rockType, s.region, s.stones, s.sectors, s.lines,
            s.boulderLines, s.routeLines, s.hardest,
            s.grades.map { AssistantGradeBand(it.band, it.count) }, s.bestMonths
        )
    }
)

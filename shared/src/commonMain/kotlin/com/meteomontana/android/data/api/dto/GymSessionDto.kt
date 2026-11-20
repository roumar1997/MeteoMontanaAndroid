package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentStats
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymSessionSource
import com.meteomontana.android.domain.model.gym.GymTrainingPeriod
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Contrato gym-sessions.json (backend en staging). [date] es yyyy-MM-dd. Lo desconocido no tira la lista. */
@Serializable
data class GymSessionDto(
    val id: Long = 0,
    val gymId: Long,
    val date: String,
    val startedAt: String? = null,
    val source: String? = null,
    /** Lo hecho ese dia por color o grado; [] si nada. */
    val ascents: List<GymAscentDto> = emptyList()
)

@Serializable
data class GymAscentDto(val key: String = "", val count: Int = 0)

/** Cuerpo de PUT gyms/sessions/{id}/ascents: SUSTITUYE todo lo anterior ([] lo borra). */
@Serializable
data class GymAscentsRequestDto(val ascents: List<GymAscentDto>)

/** GET users/{id}/gym-ascents?period=&date=&gymId= (contrato gym-sessions.json, ascentStats). */
@Serializable
data class GymAscentStatsDto(
    val period: String? = null,
    val from: String = "",
    val to: String = "",
    val sessions: Int = 0,
    val total: Int = 0,
    val items: List<GymAscentDto> = emptyList()
)

/** GET users/{uidOrUsername}/gym-stats?year= (contrato gym-sessions.json). */
@Serializable
data class GymStatsDto(
    val year: Int = 0,
    val sessionsThisWeek: Int = 0,
    val sessionsThisMonth: Int = 0,
    val sessionsThisYear: Int = 0,
    val topGym: GymTopDto? = null,
    val streakWeeks: Int = 0,
    val bestStreakWeeks: Int = 0,
    val gymDays: Int = 0,
    val outdoorDays: Int = 0
)

@Serializable
data class GymTopDto(val gymId: Long, val name: String = "", val city: String = "", val visits: Int = 0)

fun GymAscentDto.toDomain() = GymAscent(key, count)

fun GymAscent.toDto() = GymAscentDto(key, count)

fun GymSessionDto.toDomainOrNull(): GymSession? = runCatching {
    GymSession(gymId, LocalDate.parse(date), GymSessionSource.parse(source), id, ascents.filter { it.count > 0 }.map { it.toDomain() })
}.getOrNull()

fun GymSession.toDto() =
    GymSessionDto(id = id, gymId = gymId, date = date.toString(), source = source.name, ascents = ascents.map { it.toDto() })

fun GymAscentStatsDto.toDomainOrNull(): GymAscentStats? = runCatching {
    GymAscentStats(
        GymTrainingPeriod.entries.first { it.name == period }, LocalDate.parse(from), LocalDate.parse(to),
        sessions, total, items.map { it.toDomain() }
    )
}.getOrNull()

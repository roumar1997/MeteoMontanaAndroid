package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymSessionSource
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Contrato gym-sessions.json (backend en staging). [date] es yyyy-MM-dd. Lo desconocido no tira la lista. */
@Serializable
data class GymSessionDto(
    val id: Long = 0,
    val gymId: Long,
    val date: String,
    val startedAt: String? = null,
    val source: String? = null
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

fun GymSessionDto.toDomainOrNull(): GymSession? =
    runCatching { GymSession(gymId, LocalDate.parse(date), GymSessionSource.parse(source)) }.getOrNull()

fun GymSession.toDto() = GymSessionDto(gymId = gymId, date = date.toString(), source = source.name)

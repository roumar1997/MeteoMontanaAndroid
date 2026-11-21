package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.gym.Attempts
import com.meteomontana.android.domain.model.gym.BoardAscentRow
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.BoardProblem
import com.meteomontana.android.domain.model.gym.NamedBoardProblem
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentStats
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymSummary
import com.meteomontana.android.domain.model.gym.GymSummaryDay
import com.meteomontana.android.domain.model.gym.GymSummaryGym
import com.meteomontana.android.domain.model.gym.GymSummaryMonth
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
    val ascents: List<GymAscentDto> = emptyList(),
    /** Bloques de board con nombre (opcional); servidores antiguos no lo mandan. */
    val boardProblems: List<BoardProblemDto> = emptyList()
)

@Serializable
data class GymAscentDto(val key: String = "", val count: Int = 0, val attempts: Int? = null)

@Serializable
data class BoardProblemDto(val board: String = "", val grade: String = "", val name: String = "", val attempts: Int? = null)

/** Un bloque con nombre del resumen: donde y cuando. */
@Serializable
data class NamedProblemDto(val gymId: Long = 0, val date: String = "", val board: String = "", val grade: String = "", val name: String = "", val attempts: Int? = null)

fun BoardProblemDto.toDomainOrNull(): BoardProblem? {
    val b = BoardKind.entries.firstOrNull { it.wire == board } ?: return null
    return if (name.isBlank() || grade.isBlank()) null else BoardProblem(b, grade, name, Attempts.clean(attempts))
}

fun BoardProblem.toDto() = BoardProblemDto(board.wire, grade, name, attempts)

/** Cuerpo de PUT gyms/sessions/{id}/ascents: SUSTITUYE todo lo anterior ([] lo borra). */
@Serializable
data class GymAscentsRequestDto(val ascents: List<GymAscentDto>, val boardProblems: List<BoardProblemDto>? = null)

/** Un dia de boards SIN rocodromo (GET/PUT me/board-sessions): no lleva gymId ni id. */
@Serializable
data class BoardDayDto(val date: String = "", val ascents: List<GymAscentDto> = emptyList(), val boardProblems: List<BoardProblemDto> = emptyList())

/** Como una sesion con [GymSession.NO_GYM]. */
fun BoardDayDto.toDomainOrNull(): GymSession? = runCatching {
    GymSession(GymSession.NO_GYM, LocalDate.parse(date), GymSessionSource.MANUAL, 0, ascents.filter { it.count > 0 }.map { it.toDomain() }, boardProblems.mapNotNull { it.toDomainOrNull() })
}.getOrNull()

/** Cuerpo de POST gyms/{id}/sessions: la fecha local (yyyy-MM-dd) del entreno (Ayer / Otro dia apuntado sin red). */
@Serializable
data class GymSessionDateRequestDto(val date: String)

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

fun GymAscentDto.toDomain() = GymAscent(key, count, Attempts.clean(attempts))

fun GymAscent.toDto() = GymAscentDto(key, count, attempts)

fun GymSessionDto.toDomainOrNull(): GymSession? = runCatching {
    GymSession(gymId, LocalDate.parse(date), GymSessionSource.parse(source), id, ascents.filter { it.count > 0 }.map { it.toDomain() },
        boardProblems.mapNotNull { it.toDomainOrNull() })
}.getOrNull()

fun GymSession.toDto() =
    GymSessionDto(id = id, gymId = gymId, date = date.toString(), source = source.name, ascents = ascents.map { it.toDto() }, boardProblems = boardProblems.map { it.toDto() })

fun GymAscentStatsDto.toDomainOrNull(): GymAscentStats? = runCatching {
    GymAscentStats(
        GymTrainingPeriod.entries.first { it.name == period }, LocalDate.parse(from), LocalDate.parse(to),
        sessions, total, items.map { it.toDomain() }
    )
}.getOrNull()

/** GET users/{uidOrUsername}/gym-summary: todo el historial de rocódromo (contrato gym-sessions.json). */
@Serializable
data class GymSummaryDto(
    val sessions: Int = 0,
    val gyms: Int = 0,
    val ascents: Int = 0,
    val maxGrade: String? = null,
    val byGym: List<GymSummaryGymDto> = emptyList(),
    val items: List<GymAscentDto> = emptyList(),
    val byMonth: List<GymSummaryMonth> = emptyList(),
    val homeGym: HomeGymDto? = null,
    /** Orden de las categorias de Ascensiones que eligio («BOARDS,BLOCKS,ROUTES»); null = el de siempre. */
    val ascentOrder: String? = null,
    /** Orden de sus rocodromos (ids «5,3,9»); null = el de siempre. */
    val gymOrder: String? = null,
    /** Bloques de board con nombre de todo el historial (mas recientes primero). */
    val boardProblems: List<NamedProblemDto> = emptyList(),
    /** Lo hecho en boards por dia y clave de todo el historial (para listar los bloques SIN nombre); servidores antiguos no lo mandan. */
    val boardAscents: List<BoardAscentDto> = emptyList()
)

@Serializable
data class BoardAscentDto(val gymId: Long = 0, val date: String = "", val key: String = "", val count: Int = 0, val attempts: Int? = null)

@Serializable
data class HomeGymDto(val id: Long = 0, val name: String = "", val city: String? = null)

/** PUT/GET me/home-gym: {gymId} para fijar (null = quitar) y {homeGym} en la respuesta. */
@Serializable
data class HomeGymRequestDto(val gymId: Long? = null)

@Serializable
data class HomeGymResponseDto(val homeGym: HomeGymDto? = null)

/** PUT/GET me/ascent-order: {order} para fijar y en la respuesta (null = no ha elegido). */
@Serializable
data class AscentOrderDto(val order: String? = null)

/** PUT/GET me/gym-order: {order} con ids de rocodromo («5,3,9»); null en la respuesta = no ha elegido. */
@Serializable
data class GymOrderDto(val order: String? = null)

fun HomeGymDto.toDomain() = com.meteomontana.android.domain.model.gym.HomeGym(id, name, city?.takeIf { it.isNotBlank() })

@Serializable
data class GymSummaryGymDto(
    val gymId: Long = 0, val name: String? = null, val sessions: Int = 0, val ascents: Int = 0, val maxGrade: String? = null,
    val items: List<GymAscentDto> = emptyList(), val days: List<GymSummaryDayDto> = emptyList()
)

@Serializable
data class GymSummaryDayDto(val date: String = "", val ascents: Int = 0, val items: List<GymAscentDto> = emptyList(), val boardProblems: List<BoardProblemDto> = emptyList())

fun GymSummaryDto.toDomain() = GymSummary(
    sessions, gyms, ascents, maxGrade?.takeIf { it.isNotBlank() },
    byGym.map { g ->
        GymSummaryGym(
            g.gymId, g.name, g.sessions, g.ascents, g.maxGrade?.takeIf { m -> m.isNotBlank() },
            g.items.filter { it.key.isNotBlank() && it.count > 0 }.map { it.toDomain() },
            g.days.filter { it.date.isNotBlank() }.map { GymSummaryDay(it.date, it.ascents, it.items.filter { a -> a.key.isNotBlank() && a.count > 0 }.map { a -> a.toDomain() }, it.boardProblems.mapNotNull { p -> p.toDomainOrNull() }) }
        )
    },
    items.filter { it.key.isNotBlank() && it.count > 0 }.map { it.toDomain() }, byMonth,
    homeGym?.takeIf { it.id != 0L && it.name.isNotBlank() }?.toDomain(),
    ascentOrder?.takeIf { it.isNotBlank() },
    gymOrder?.takeIf { it.isNotBlank() },
    boardProblems.mapNotNull { it.toNamedOrNull() },
    boardAscents.filter { it.key.isNotBlank() && it.date.isNotBlank() && it.count > 0 }.map { BoardAscentRow(it.gymId, it.date, it.key, it.count, Attempts.clean(it.attempts)) }
)

fun NamedProblemDto.toNamedOrNull(): NamedBoardProblem? =
    BoardProblemDto(board, grade, name, attempts).toDomainOrNull()?.let { NamedBoardProblem(gymId, date, it) }

package com.meteomontana.android.domain.model.gym

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Como mide la dificultad un rocodromo: por colores (de facil a dificil) o por grado frances. */
enum class GymScaleType {
    COLORS, GRADES;

    companion object {
        fun parse(raw: String?): GymScaleType = entries.firstOrNull { it.name == raw } ?: GRADES
    }
}

/** Un color de la escala. [hex] es `#RRGGBB`; los grados son opcionales (hay escalas sin equivalencia oficial). */
data class GymScaleLevel(val name: String, val hex: String, val gradeFrom: String? = null, val gradeTo: String? = null)

data class GymScale(val type: GymScaleType, val levels: List<GymScaleLevel> = emptyList()) {
    val isColors: Boolean get() = type == GymScaleType.COLORS && levels.isNotEmpty()

    /** Nivel cuyo nombre es [key] (la clave con la que el servidor guarda un color en `ascents`). */
    fun levelNamed(key: String): GymScaleLevel? = levels.firstOrNull { it.name.equals(key, ignoreCase = true) }
}

/** Colores que se ofrecen al proponer una escala (los de Sputnik mas los habituales); el nombre es la clave del servidor. */
object GymScalePresets {
    val colors: List<GymScaleLevel> = listOf(
        GymScaleLevel("blanco", "#FFFFFF"), GymScaleLevel("amarillo", "#F2C21A"), GymScaleLevel("naranja", "#F28C28"),
        GymScaleLevel("verde", "#2E9E4F"), GymScaleLevel("azul", "#1F6FD1"), GymScaleLevel("morado", "#7B4FC4"),
        GymScaleLevel("rosa", "#E55FA0"), GymScaleLevel("rojo", "#C53030"), GymScaleLevel("marron", "#8A5A3C"),
        GymScaleLevel("gris", "#8E9199"), GymScaleLevel("negro", "#1B1B1B")
    )

    const val MIN_LEVELS = 2

    /** Escala propuesta: los colores tocados, EN ESE ORDEN (de facil a dificil), o por grados. Null si faltan colores. */
    fun proposal(type: GymScaleType, pickedNames: List<String>): GymScale? = when (type) {
        GymScaleType.GRADES -> GymScale(GymScaleType.GRADES)
        GymScaleType.COLORS -> pickedNames.mapNotNull { n -> colors.firstOrNull { it.name == n } }.distinctBy { it.name }
            .takeIf { it.size >= MIN_LEVELS }?.let { GymScale(GymScaleType.COLORS, it) }
    }
}

/** Lo hecho en una sesion: [key] es un color de la escala o un grado, [count] cuantas veces. */
data class GymAscent(val key: String, val count: Int)

/** Periodo de las estadisticas de entrenos. Semana de lunes a domingo (igual que el servidor). */
enum class GymTrainingPeriod {
    WEEK, MONTH, YEAR;

    /** Primer y ultimo dia (incluidos) del periodo que contiene [date]. */
    fun range(date: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        WEEK -> {
            val monday = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))
            monday to monday.plus(DatePeriod(days = 6))
        }
        MONTH -> {
            val first = LocalDate(date.year, date.monthNumber, 1)
            first to first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        }
        YEAR -> LocalDate(date.year, 1, 1) to LocalDate(date.year, 12, 31)
    }
}

/** Respuesta de `GET users/{id}/gym-ascents` (o el mismo calculo hecho en el movil sin red). */
data class GymAscentStats(
    val period: GymTrainingPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val sessions: Int,
    val total: Int,
    val items: List<GymAscent>
)

/** Resumen del periodo para la pantalla de entrenos (KPIs y sesiones, de la mas reciente a la mas antigua). */
data class GymTrainingSummary(
    val period: GymTrainingPeriod,
    val sessions: List<GymSession>,
    val gyms: Int,
    val ascents: Int
)

/** Calculo puro de entrenos y ascensiones (igual en iOS y Android). */
object GymTraining {

    fun summary(all: List<GymSession>, period: GymTrainingPeriod, date: LocalDate): GymTrainingSummary {
        val (from, to) = period.range(date)
        val inRange = all.filter { it.date in from..to && it.hasGym }.distinctBy { it.gymId to it.date }
            .sortedWith(compareByDescending<GymSession> { it.date }.thenBy { it.gymId })
        return GymTrainingSummary(period, inRange, inRange.map { it.gymId }.toSet().size, inRange.sumOf { it.totalAscents })
    }

    /** Ascensiones sumadas por clave; con [gymId] solo las de ese rocodromo. Orden: mas hechas primero. */
    fun stats(all: List<GymSession>, period: GymTrainingPeriod, date: LocalDate, gymId: Long? = null): GymAscentStats {
        val (from, to) = period.range(date)
        val sessions = all.filter { it.date in from..to && (gymId == null || it.gymId == gymId) }
            .distinctBy { it.gymId to it.date }
        val items = sessions.flatMap { it.ascents }.groupBy { it.key }
            .map { (key, list) -> GymAscent(key, list.sumOf { it.count }) }
            .sortedWith(compareByDescending<GymAscent> { it.count }.thenBy { it.key })
        return GymAscentStats(period, from, to, sessions.count { it.hasGym }, items.sumOf { it.count }, items)
    }

    /** Suma [delta] a una clave (sin bajar de 0 ni pasar de [MAX_COUNT]) y devuelve la lista sin ceros. */
    fun adjust(ascents: List<GymAscent>, key: String, delta: Int): List<GymAscent> {
        val current = countOf(ascents, key)
        val next = (current + delta).coerceIn(0, MAX_COUNT)
        val rest = ascents.filterNot { it.key == key }
        return when {
            next == 0 -> rest
            current == 0 -> rest + GymAscent(key, next)
            else -> ascents.map { if (it.key == key) GymAscent(key, next) else it }
        }
    }

    fun countOf(ascents: List<GymAscent>, key: String): Int = ascents.firstOrNull { it.key == key }?.count ?: 0

    /** Grados que se ofrecen para apuntar en un rocodromo sin escala de colores (de 4 a 8a, como la maqueta). */
    val DEFAULT_GRADES: List<String> = listOf("4", "5", "6a", "6b", "6c", "7a", "7b", "8a")

    const val MAX_COUNT = 999
}

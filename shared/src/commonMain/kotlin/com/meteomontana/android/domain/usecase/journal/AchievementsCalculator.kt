package com.meteomontana.android.domain.usecase.journal

import com.meteomontana.android.domain.model.JournalSession

/** Familia de un logro: las apps escriben el texto con la familia + [Achievement.param] (ES/EN). */
enum class AchievementKind { GRADE, VOLUME, FLASHES, ONSIGHTS, FLASH_MAX, ONSIGHT_MAX, SCHOOLS, REGIONS, ABROAD,
    WEEKENDS, DAYS_IN_YEAR, EVERY_MONTH, PROJECTS }

/**
 * Un logro del catálogo. [param] = el número o el grado que pide ("7a", "100"…); [discipline] solo en los de grado.
 * [unlockedOn] = día ("yyyy-MM-dd") en que se consiguió, si se sabe.
 */
data class Achievement(
    val id: String,
    val kind: AchievementKind,
    val param: String,
    val discipline: String?,
    val unlocked: Boolean,
    val unlockedOn: String?,
)

/** Región y país de una escuela, para los logros de explorar (los da el catálogo de escuelas). */
data class SchoolPlace(val region: String?, val country: String?)

/**
 * Logros calculados con el diario (todo en el móvil, sin conexión). Solo cuentan las ascensiones HECHAS.
 * Primera versión (PLAN_MEJORAS_2026-10 §D): grado, volumen, estilo, explorar, constancia y proyectos. Los de
 * comunidad (aportaciones, betas, quedadas, votos) llegarán con las recompensas.
 */
object AchievementsCalculator {

    val GRADE_STEPS = listOf("6a", "6c", "7a", "7b", "7c", "8a")
    val VOLUME_STEPS = listOf(10, 50, 100, 250, 500)
    val SCHOOL_STEPS = listOf(5, 10, 25)

    fun compute(all: List<JournalSession>, places: Map<String, SchoolPlace>, today: String): List<Achievement> {
        val done = all.filter { it.status == "DONE" }.sortedBy { it.date }
        val out = mutableListOf<Achievement>()

        for (disc in listOf("BOULDER", "ROUTE")) {
            val inDisc = done.filter { it.discipline == disc }
            for (g in GRADE_STEPS) {
                val first = inDisc.firstOrNull { JournalStatsCalculator.gradeRank(it.grade ?: "") >= JournalStatsCalculator.gradeRank(g) }
                out += Achievement("grade_${disc.lowercase()}_$g", AchievementKind.GRADE, g, disc, first != null, first?.date)
            }
        }
        for (n in VOLUME_STEPS) {
            out += Achievement("volume_$n", AchievementKind.VOLUME, "$n", null, done.size >= n, done.getOrNull(n - 1)?.date)
        }
        done.filter { it.alFlash }.let { f ->
            out += Achievement("flashes_10", AchievementKind.FLASHES, "10", null, f.size >= 10, f.getOrNull(9)?.date)
        }
        done.filter { it.aVista }.let { o ->
            out += Achievement("onsights_10", AchievementKind.ONSIGHTS, "10", null, o.size >= 10, o.getOrNull(9)?.date)
        }
        out += styleAtMax("flash_max", AchievementKind.FLASH_MAX, done) { it.alFlash }
        out += styleAtMax("onsight_max", AchievementKind.ONSIGHT_MAX, done) { it.aVista }

        val firstPerSchool = done.filter { it.schoolId != null }.distinctBy { it.schoolId }
        for (n in SCHOOL_STEPS) {
            out += Achievement("schools_$n", AchievementKind.SCHOOLS, "$n", null, firstPerSchool.size >= n,
                firstPerSchool.getOrNull(n - 1)?.date)
        }
        val firstPerRegion = firstPerSchool.mapNotNull { e -> places[e.schoolId]?.region?.takeIf { it.isNotBlank() }?.let { it to e } }
            .distinctBy { it.first }
        out += Achievement("regions_3", AchievementKind.REGIONS, "3", null, firstPerRegion.size >= 3,
            firstPerRegion.getOrNull(2)?.second?.date)
        val abroad = firstPerSchool.firstOrNull { e -> places[e.schoolId]?.country?.let { it.isNotBlank() && it != "ES" } == true }
        out += Achievement("abroad_1", AchievementKind.ABROAD, "1", null, abroad != null, abroad?.date)

        val weekendStreak = weekendStreakDate(done.map { it.date }, 4)
        out += Achievement("weekends_4", AchievementKind.WEEKENDS, "4", null, weekendStreak != null, weekendStreak)
        val days20 = done.map { it.date }.distinct().groupBy { it.take(4) }.entries
            .firstOrNull { it.value.size >= 20 }?.value?.sorted()?.get(19)
        out += Achievement("days_20", AchievementKind.DAYS_IN_YEAR, "20", null, days20 != null, days20)
        val fullYear = done.groupBy { it.date.take(4) }.entries.sortedBy { it.key }
            .firstOrNull { (_, es) -> es.map { it.date.substring(5, 7) }.toSet().size == 12 }
        out += Achievement("every_month", AchievementKind.EVERY_MONTH, "12", null, fullYear != null,
            fullYear?.value?.maxOf { it.date })

        val fallen = fallenProjects(all)
        out += Achievement("projects_1", AchievementKind.PROJECTS, "1", null, fallen.isNotEmpty(), fallen.getOrNull(0))
        out += Achievement("projects_5", AchievementKind.PROJECTS, "5", null, fallen.size >= 5, fallen.getOrNull(4))
        return out
    }

    /** Flash / a vista de tu grado máximo (en cualquiera de las dos modalidades). */
    private fun styleAtMax(id: String, kind: AchievementKind, done: List<JournalSession>,
                           style: (JournalSession) -> Boolean): Achievement {
        val hit = listOf("BOULDER", "ROUTE").mapNotNull { disc ->
            val inDisc = done.filter { it.discipline == disc && !it.grade.isNullOrBlank() }
            val max = inDisc.maxOfOrNull { JournalStatsCalculator.gradeRank(it.grade!!) } ?: return@mapNotNull null
            if (max == 0) null else inDisc.firstOrNull { style(it) && JournalStatsCalculator.gradeRank(it.grade!!) == max }
        }.minByOrNull { it.date }
        return Achievement(id, kind, "1", null, hit != null, hit?.date)
    }

    /** Días (ordenados) en que cayó un proyecto: HECHA después de haberla tenido como PROYECTO. */
    fun fallenProjects(all: List<JournalSession>): List<String> {
        fun key(e: JournalSession) = e.lineId ?: "${e.schoolId}|${e.blockName.trim().lowercase()}"
        val projectSince = all.filter { it.status == "PROJECT" }.groupBy(::key).mapValues { (_, v) -> v.minOf { it.date } }
        return all.filter { it.status == "DONE" }
            .filter { e -> projectSince[key(e)]?.let { it <= e.date } == true }
            .distinctBy(::key).map { it.date }.sorted()
    }

    /** Día en que se completan [n] fines de semana SEGUIDOS con salida, o null. */
    private fun weekendStreakDate(dates: List<String>, n: Int): String? {
        val weekendDates = dates.filter { isWeekend(it) }.sorted()
        // Ordenado por semana (toSortedMap es solo de JVM; esto vale también en iOS).
        val weeks = weekendDates.groupBy { JournalStatsCalculator.epochWeek(it) }.entries.sortedBy { it.key }
            .associate { it.key to it.value }
        var run = 0
        var prev: Int? = null
        for ((week, ds) in weeks) {
            run = if (prev != null && week == prev + 1) run + 1 else 1
            prev = week
            if (run >= n) return ds.first()
        }
        return null
    }

    private fun isWeekend(date: String): Boolean {
        // epochWeek cuenta semanas de lunes a domingo: el día de la semana sale de (epochDay + 3) % 7 (0 = lunes).
        val dow = (JournalStatsCalculator.epochDayOf(date) + 3).mod(7)
        return dow == 5 || dow == 6
    }
}

/** Objetivo del año (opcional). [discipline] solo cuenta para el grado. */
data class YearGoal(val year: Int, val discipline: String?, val grade: String?, val ascents: Int?, val days: Int?)

/** Cómo vas con el objetivo: lo hecho este año frente a lo propuesto. */
data class GoalProgress(
    val goal: YearGoal,
    val ascentsDone: Int,
    val daysDone: Int,
    /** Grado máximo de este año en la modalidad del objetivo (null si aún nada). */
    val maxGradeThisYear: String?,
    val gradeReached: Boolean,
)

/** Extras de la pantalla: % de flash y a vista, y este año frente al anterior. */
data class StyleShare(val total: Int, val flashPct: Int, val onsightPct: Int)

data class YearComparison(val year: String, val ascents: Int, val previousAscents: Int, val days: Int,
                          val previousDays: Int, val maxGrade: String?, val previousMaxGrade: String?)

object GoalCalculator {

    fun progress(goal: YearGoal, all: List<JournalSession>): GoalProgress {
        val y = goal.year.toString()
        val done = all.filter { it.status == "DONE" && it.date.startsWith(y) }
        val inDisc = done.filter { goal.discipline == null || it.discipline == goal.discipline }
        val max = inDisc.mapNotNull { it.grade?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .maxByOrNull { JournalStatsCalculator.gradeRank(it) }
        val reached = goal.grade != null && max != null &&
            JournalStatsCalculator.gradeRank(max) >= JournalStatsCalculator.gradeRank(goal.grade)
        return GoalProgress(goal, done.size, done.map { it.date }.distinct().size, max, reached)
    }

    fun styleShare(entries: List<JournalSession>): StyleShare {
        val done = entries.filter { it.status == "DONE" }
        if (done.isEmpty()) return StyleShare(0, 0, 0)
        return StyleShare(done.size, done.count { it.alFlash } * 100 / done.size, done.count { it.aVista } * 100 / done.size)
    }

    /** Año [year] frente al anterior, con las entradas ya filtradas por modalidad. */
    fun compareYears(entries: List<JournalSession>, year: String): YearComparison {
        val prev = (year.toInt() - 1).toString()
        fun of(y: String) = entries.filter { it.status == "DONE" && it.date.startsWith(y) }
        fun maxOf(es: List<JournalSession>) = es.mapNotNull { it.grade?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .maxByOrNull { JournalStatsCalculator.gradeRank(it) }
        val now = of(year)
        val before = of(prev)
        return YearComparison(year, now.size, before.size, now.map { it.date }.distinct().size,
            before.map { it.date }.distinct().size, maxOf(now), maxOf(before))
    }
}

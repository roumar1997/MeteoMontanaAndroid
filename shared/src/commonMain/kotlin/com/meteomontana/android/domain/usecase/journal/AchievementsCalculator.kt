package com.meteomontana.android.domain.usecase.journal

import com.meteomontana.android.domain.model.JournalSession

/** Familia de un logro: las apps escriben el texto con la familia + [Achievement.param] (ES/EN). */
enum class AchievementKind { GRADE, DAY_GRADE, TOTAL_GRADE, VOLUME, FLASHES, ONSIGHTS, FLASH_MAX, ONSIGHT_MAX,
    SCHOOLS, REGIONS, ABROAD, WEEKENDS, DAYS_IN_YEAR, EVERY_MONTH, PROJECTS }

/** Dificultad de diseño de un logro (cuando haya datos de verdad, manda el % de gente que lo tiene). */
enum class AchievementTier { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY;
    fun bumped(): AchievementTier = entries[minOf(ordinal + 1, entries.lastIndex)]
}

/** Sección donde se enseña en la pantalla de logros. */
enum class AchievementGroup { GRADES, IN_A_DAY, TOTALS, VOLUME, STYLE, EXPLORE, CONSISTENCY, PROJECTS }

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
    val tier: AchievementTier = AchievementTier.COMMON,
    val group: AchievementGroup = AchievementGroup.VOLUME,
    /** Cuántas hacen falta (3 en un día, 10 en total…); 0 si el número va en [param]. */
    val count: Int = 0,
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
    /** Grados de los logros «3 en un día» y «10 en total» (de 5c a 8a, en bloque y en vía). */
    val LADDER_GRADES = listOf("5c", "6a", "6b", "6c", "7a", "7b", "7c", "8a")
    const val PER_DAY = 3
    const val PER_TOTAL = 10
    val VOLUME_STEPS = listOf(10, 50, 100, 250, 500)
    val SCHOOL_STEPS = listOf(5, 10, 25)

    /** Dificultad de diseño de un grado: sirve de base hasta que haya datos reales de cuánta gente lo tiene. */
    fun baseTier(grade: String): AchievementTier = when (grade) {
        "5c", "6a", "6b" -> AchievementTier.COMMON
        "6c" -> AchievementTier.UNCOMMON
        "7a", "7b" -> AchievementTier.RARE
        "7c" -> AchievementTier.EPIC
        else -> AchievementTier.LEGENDARY
    }

    /** Todos los logros del catálogo, sin conseguir. */
    val catalog: List<Achievement> by lazy { compute(emptyList(), emptyMap(), "") }

    /** El logro con ese id (para enseñar el destacado de otra persona), o null si el id ya no existe. */
    fun byId(id: String): Achievement? = catalog.firstOrNull { it.id == id }

    private fun rank(grade: String?) = JournalStatsCalculator.gradeRank(grade ?: "")
    private fun lineKey(e: JournalSession) = e.lineId ?: "${e.schoolId}|${e.blockName.trim().lowercase()}"

    fun compute(all: List<JournalSession>, places: Map<String, SchoolPlace>, today: String): List<Achievement> {
        val done = all.filter { it.status == "DONE" }.sortedBy { it.date }
        val out = mutableListOf<Achievement>()
        fun add(a: Achievement) { out += a }

        for (disc in listOf("BOULDER", "ROUTE")) {
            val inDisc = done.filter { it.discipline == disc }
            val d = disc.lowercase()
            for (g in GRADE_STEPS) {
                val first = inDisc.firstOrNull { rank(it.grade) >= rank(g) }
                add(Achievement("grade_${d}_$g", AchievementKind.GRADE, g, disc, first != null, first?.date,
                    baseTier(g), AchievementGroup.GRADES))
            }
            val byDay = inDisc.groupBy { it.date }
            val uniqueRoutes = inDisc.distinctBy(::lineKey)
            for (g in LADDER_GRADES) {
                // «3 de 7c o más en un día»: tres ascensiones HECHAS ese día de ese grado o más duras.
                val day = byDay.entries.sortedBy { it.key }
                    .firstOrNull { (_, es) -> es.count { rank(it.grade) >= rank(g) } >= PER_DAY }?.key
                add(Achievement("day_${d}_$g", AchievementKind.DAY_GRADE, g, disc, day != null, day,
                    baseTier(g).bumped(), AchievementGroup.IN_A_DAY, PER_DAY))
                // «10 de 7a o más en total»: diez vías distintas.
                val hard = uniqueRoutes.filter { rank(it.grade) >= rank(g) }
                add(Achievement("total_${d}_$g", AchievementKind.TOTAL_GRADE, g, disc, hard.size >= PER_TOTAL,
                    hard.getOrNull(PER_TOTAL - 1)?.date, baseTier(g), AchievementGroup.TOTALS, PER_TOTAL))
            }
        }
        val volumeTiers = listOf(AchievementTier.COMMON, AchievementTier.UNCOMMON, AchievementTier.RARE,
            AchievementTier.EPIC, AchievementTier.LEGENDARY)
        VOLUME_STEPS.forEachIndexed { i, n ->
            add(Achievement("volume_$n", AchievementKind.VOLUME, "$n", null, done.size >= n, done.getOrNull(n - 1)?.date,
                volumeTiers[i], AchievementGroup.VOLUME))
        }
        done.filter { it.alFlash }.let { f ->
            add(Achievement("flashes_10", AchievementKind.FLASHES, "10", null, f.size >= 10, f.getOrNull(9)?.date,
                AchievementTier.UNCOMMON, AchievementGroup.STYLE))
        }
        done.filter { it.aVista }.let { o ->
            add(Achievement("onsights_10", AchievementKind.ONSIGHTS, "10", null, o.size >= 10, o.getOrNull(9)?.date,
                AchievementTier.UNCOMMON, AchievementGroup.STYLE))
        }
        add(styleAtMax("flash_max", AchievementKind.FLASH_MAX, done, AchievementTier.RARE) { it.alFlash })
        add(styleAtMax("onsight_max", AchievementKind.ONSIGHT_MAX, done, AchievementTier.EPIC) { it.aVista })

        val firstPerSchool = done.filter { it.schoolId != null }.distinctBy { it.schoolId }
        val schoolTiers = listOf(AchievementTier.COMMON, AchievementTier.UNCOMMON, AchievementTier.RARE)
        SCHOOL_STEPS.forEachIndexed { i, n ->
            add(Achievement("schools_$n", AchievementKind.SCHOOLS, "$n", null, firstPerSchool.size >= n,
                firstPerSchool.getOrNull(n - 1)?.date, schoolTiers[i], AchievementGroup.EXPLORE))
        }
        val firstPerRegion = firstPerSchool.mapNotNull { e -> places[e.schoolId]?.region?.takeIf { it.isNotBlank() }?.let { it to e } }
            .distinctBy { it.first }
        add(Achievement("regions_3", AchievementKind.REGIONS, "3", null, firstPerRegion.size >= 3,
            firstPerRegion.getOrNull(2)?.second?.date, AchievementTier.UNCOMMON, AchievementGroup.EXPLORE))
        val abroad = firstPerSchool.firstOrNull { e -> places[e.schoolId]?.country?.let { it.isNotBlank() && it != "ES" } == true }
        add(Achievement("abroad_1", AchievementKind.ABROAD, "1", null, abroad != null, abroad?.date,
            AchievementTier.UNCOMMON, AchievementGroup.EXPLORE))

        val weekendStreak = weekendStreakDate(done.map { it.date }, 4)
        add(Achievement("weekends_4", AchievementKind.WEEKENDS, "4", null, weekendStreak != null, weekendStreak,
            AchievementTier.UNCOMMON, AchievementGroup.CONSISTENCY))
        val days20 = done.map { it.date }.distinct().groupBy { it.take(4) }.entries
            .firstOrNull { it.value.size >= 20 }?.value?.sorted()?.get(19)
        add(Achievement("days_20", AchievementKind.DAYS_IN_YEAR, "20", null, days20 != null, days20,
            AchievementTier.UNCOMMON, AchievementGroup.CONSISTENCY))
        val fullYear = done.groupBy { it.date.take(4) }.entries.sortedBy { it.key }
            .firstOrNull { (_, es) -> es.map { it.date.substring(5, 7) }.toSet().size == 12 }
        add(Achievement("every_month", AchievementKind.EVERY_MONTH, "12", null, fullYear != null,
            fullYear?.value?.maxOf { it.date }, AchievementTier.EPIC, AchievementGroup.CONSISTENCY))

        val fallen = fallenProjects(all)
        add(Achievement("projects_1", AchievementKind.PROJECTS, "1", null, fallen.isNotEmpty(), fallen.getOrNull(0),
            AchievementTier.UNCOMMON, AchievementGroup.PROJECTS))
        add(Achievement("projects_5", AchievementKind.PROJECTS, "5", null, fallen.size >= 5, fallen.getOrNull(4),
            AchievementTier.RARE, AchievementGroup.PROJECTS))
        return out
    }

    /** Flash / a vista de tu grado máximo (en cualquiera de las dos modalidades). */
    private fun styleAtMax(id: String, kind: AchievementKind, done: List<JournalSession>, tier: AchievementTier,
                           style: (JournalSession) -> Boolean): Achievement {
        val hit = listOf("BOULDER", "ROUTE").mapNotNull { disc ->
            val inDisc = done.filter { it.discipline == disc && !it.grade.isNullOrBlank() }
            val max = inDisc.maxOfOrNull { rank(it.grade) } ?: return@mapNotNull null
            if (max == 0) null else inDisc.firstOrNull { style(it) && rank(it.grade) == max }
        }.minByOrNull { it.date }
        return Achievement(id, kind, "1", null, hit != null, hit?.date, tier, AchievementGroup.STYLE)
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

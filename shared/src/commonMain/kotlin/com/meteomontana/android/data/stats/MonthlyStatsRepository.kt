package com.meteomontana.android.data.stats

import com.meteomontana.android.data.api.KtorSchoolApi
import com.meteomontana.db.MeteoMontanaDb
import kotlinx.datetime.Clock

private const val CACHE_TTL_MS = 180L * 24 * 3600 * 1000L

data class MonthlyStats(val scores: List<Int>, val bestRange: String?)

/**
 * Scores mensuales de una escuela. El cálculo (3 años de histórico Open-Meteo)
 * vive en el backend (GET /api/schools/{id}/monthly-stats, cacheado allí con
 * Caffeine); aquí solo pedimos el resultado y lo cacheamos en SQLDelight para
 * soporte offline.
 */
class MonthlyStatsRepository(
    private val db: MeteoMontanaDb,
    private val api: KtorSchoolApi
) {
    suspend fun get(schoolId: String, lat: Double, lon: Double, rockType: String?): MonthlyStats {
        val cached = db.schemaQueries.findMonthly(schoolId).executeAsOneOrNull()
        val now = Clock.System.now().toEpochMilliseconds()
        if (cached != null && now - cached.fetchedAt < CACHE_TTL_MS) {
            return MonthlyStats(
                scores = decodeList(cached.monthsJson),
                bestRange = cached.bestRange
            )
        }
        val fresh = api.getMonthlyStats(schoolId)
        db.schemaQueries.upsertMonthly(
            schoolId = schoolId,
            monthsJson = encodeList(fresh.scores),
            bestRange = fresh.bestRange,
            fetchedAt = now
        )
        return MonthlyStats(fresh.scores, fresh.bestRange)
    }

    private fun encodeList(scores: List<Int>): String = scores.joinToString(",")
    private fun decodeList(s: String): List<Int> =
        if (s.isBlank()) emptyList()
        else s.split(",").mapNotNull { it.trim().toIntOrNull() }
}

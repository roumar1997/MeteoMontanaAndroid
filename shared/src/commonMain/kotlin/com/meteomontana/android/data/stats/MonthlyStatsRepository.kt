package com.meteomontana.android.data.stats

import com.meteomontana.db.MeteoMontanaDb
import kotlinx.datetime.Clock

private const val CACHE_TTL_MS = 180L * 24 * 3600 * 1000L

data class MonthlyStats(val scores: List<Int>, val bestRange: String?)

class MonthlyStatsRepository(
    private val db: MeteoMontanaDb,
    private val client: OpenMeteoArchiveClient
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
        val fresh = client.fetchMonthly(lat, lon, rockType)
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

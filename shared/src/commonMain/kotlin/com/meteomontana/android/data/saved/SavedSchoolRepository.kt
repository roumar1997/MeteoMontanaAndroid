package com.meteomontana.android.data.saved

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.School
import com.meteomontana.db.MeteoMontanaDb
import com.meteomontana.db.SavedBlock
import com.meteomontana.db.SavedBlockLine
import com.meteomontana.db.SavedSchool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class SavedSchoolRepository(
    private val db: MeteoMontanaDb
) {
    private val q get() = db.schemaQueries

    fun isSavedFlow(id: String): Flow<Boolean> =
        q.isSchoolSaved(id).asFlow().mapToList(Dispatchers.Default).map { it.firstOrNull() == true }

    fun observeSaved(): Flow<List<SavedSchool>> =
        q.observeAllSchools().asFlow().mapToList(Dispatchers.Default)

    @Throws(Exception::class)
    suspend fun saveOffline(school: School, blocks: List<Block>, forecast: Forecast?) {
        val now = Clock.System.now().toEpochMilliseconds()
        q.transaction {
            q.upsertSchool(
                id = school.id, name = school.name, region = school.region,
                rockType = school.rockType, lat = school.lat, lon = school.lon, savedAt = now
            )
            q.deleteBlocksOfSchool(school.id)
            blocks.forEach { b ->
                q.insertBlock(
                    id = b.id, schoolId = school.id, type = b.type, name = b.name,
                    lat = b.lat, lon = b.lon, photoPath = b.photoPath, description = b.description
                )
                b.lines.forEach { l ->
                    q.insertLine(
                        id = l.id, blockId = b.id, number = l.sortOrder.toLong(),
                        grade = l.grade, startType = l.startType, name = l.name,
                        linePath = l.linePath, sortOrder = l.sortOrder.toLong()
                    )
                }
            }
            if (forecast != null) {
                q.upsertForecast(
                    schoolId = school.id,
                    forecastJson = runCatching { ForecastJson.encode(forecast) }.getOrDefault(""),
                    fetchedAt = now
                )
            }
        }
    }

    @Throws(Exception::class)
    suspend fun remove(id: String) {
        q.deleteSchool(id)
    }

    /**
     * Caché de forecast para CUALQUIER escuela visitada (no solo guardadas
     * offline — SavedForecast no tiene FK a SavedSchool). Permite pintar el
     * último forecast conocido al instante o cuando la red falla.
     */
    @Throws(Exception::class)
    suspend fun cacheForecast(schoolId: String, forecast: Forecast) {
        q.upsertForecast(
            schoolId = schoolId,
            forecastJson = runCatching { ForecastJson.encode(forecast) }.getOrDefault(""),
            fetchedAt = Clock.System.now().toEpochMilliseconds()
        )
    }

    /**
     * Forecast cacheado + epoch ms de cuándo se bajó, en un objeto tipado (no
     * `Pair`/KotlinPair) para consumirlo fácil desde Swift. Null si no hay.
     */
    @Throws(Exception::class)
    suspend fun cachedForecast(schoolId: String): CachedForecast? =
        loadCachedForecast(schoolId)?.let { CachedForecast(it.first, it.second) }

    /** Último forecast cacheado + epoch ms en que se bajó, o null si no hay. */
    @Throws(Exception::class)
    suspend fun loadCachedForecast(schoolId: String): Pair<Forecast, Long>? {
        val row = q.findForecast(schoolId).executeAsOneOrNull() ?: return null
        val forecast = row.forecastJson.takeIf { it.isNotBlank() }
            ?.let { runCatching { ForecastJson.decode(it) }.getOrNull() } ?: return null
        return forecast to row.fetchedAt
    }

    @Throws(Exception::class)
    suspend fun loadOffline(id: String): OfflineSnapshot? {
        val s = q.findSchool(id).executeAsOneOrNull() ?: return null
        val blocks = q.blocksOfSchool(id).executeAsList()
        val lines = if (blocks.isEmpty()) emptyList()
                    else q.linesOfBlocks(blocks.map { it.id }).executeAsList()
        val fc = q.findForecast(id).executeAsOneOrNull()
        val forecast = fc?.forecastJson?.takeIf { it.isNotBlank() }
            ?.let { runCatching { ForecastJson.decode(it) }.getOrNull() }
        return OfflineSnapshot(s, blocks, lines, forecast, fc?.fetchedAt)
    }

    fun toBlock(entity: SavedBlock, lines: List<SavedBlockLine>): Block =
        Block(
            id = entity.id, schoolId = entity.schoolId, type = entity.type,
            name = entity.name, lat = entity.lat, lon = entity.lon,
            photoPath = entity.photoPath, description = entity.description,
            createdByUid = "", createdAt = "",
            lines = lines.filter { it.blockId == entity.id }
                .sortedBy { it.sortOrder }
                .map { line ->
                    BlockLine(
                        id = line.id, name = line.name ?: "",
                        grade = line.grade, startType = line.startType,
                        linePath = line.linePath, sortOrder = line.sortOrder.toInt()
                    )
                }
        )
}

/** Forecast cacheado + epoch ms de la última descarga (para "actualizado hace X"). */
data class CachedForecast(
    val forecast: Forecast,
    val fetchedAtMillis: Long
)

data class OfflineSnapshot(
    val school: SavedSchool,
    val blocks: List<SavedBlock>,
    val lines: List<SavedBlockLine>,
    val forecast: Forecast?,
    val forecastFetchedAt: Long?
)

package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorBlockApi
import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.repository.BlockRepository
import com.meteomontana.db.MeteoMontanaDb
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Repositorio de bloques con CACHÉ DE DISCO por escuela (CachedBlocksPage):
 * cada carga buena se guarda; si la red falla, se devuelve la última buena —
 * el detalle de una escuela ya visitada abre sus piedras SIN conexión aunque
 * no esté "guardada offline". Además [getCachedBlocks] permite a las UIs
 * pintar al instante mientras la red refresca (stale-while-revalidate,
 * chip "perf diario→piedra").
 */
class KtorBlockRepository(
    private val api: KtorBlockApi,
    /** null = sin caché (tests o wiring antiguo). */
    private val db: MeteoMontanaDb? = null
) : BlockRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(BlockDto.serializer())

    override suspend fun getBlocks(schoolId: String): List<Block> {
        val cache = db ?: return api.getBlocks(schoolId).map { it.toDomain() }
        val page = try {
            api.getBlocks(schoolId)
        } catch (t: Throwable) {
            return getCachedBlocks(schoolId) ?: throw t
        }
        runCatching {
            cache.schemaQueries.upsertBlocksPage(
                schoolId,
                json.encodeToString(listSerializer, page),
                Clock.System.now().toEpochMilliseconds()
            )
        }
        return page.map { it.toDomain() }
    }

    /** Última página buena de bloques de [schoolId], o null si nunca se cargó. */
    override fun getCachedBlocks(schoolId: String): List<Block>? {
        val cache = db ?: return null
        val row = runCatching {
            cache.schemaQueries.selectBlocksPage(schoolId).executeAsOneOrNull()
        }.getOrNull() ?: return null
        return runCatching {
            json.decodeFromString(listSerializer, row.json).map { it.toDomain() }
        }.getOrNull()
    }

    override suspend fun getBlock(blockId: String): Block =
        api.getBlock(blockId).toDomain()

    override suspend fun createBlock(schoolId: String, req: CreateBlockRequest): Block =
        api.createBlock(schoolId, req).toDomain()

    override suspend fun updateBlock(blockId: String, req: CreateBlockRequest): Block =
        api.updateBlock(blockId, req).toDomain()

    override suspend fun deleteBlock(blockId: String) {
        api.deleteBlock(blockId)
    }
}

package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.domain.model.BetaLink
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.LineComment

interface BlockRepository {
    suspend fun getBlocks(schoolId: String): List<Block>

    /** Última página buena cacheada en disco de [schoolId] (stale-while-
     *  revalidate del detalle), o null si no hay caché disponible. */
    fun getCachedBlocks(schoolId: String): List<Block>? = null
    suspend fun getBlock(blockId: String): Block
    suspend fun createBlock(schoolId: String, req: CreateBlockRequest): Block
    suspend fun updateBlock(blockId: String, req: CreateBlockRequest): Block
    suspend fun deleteBlock(blockId: String)

    /** Renumera las piedras de un sector (o las sin sector) según el orden
     *  exacto que se le pase. Solo admin. */
    suspend fun reorderBlocks(schoolId: String, sectorBlockId: String?, orderedBlockIds: List<String>): List<Block>
    /** Sugiere y aplica un primer orden por distancia al parking más cercano. Solo admin. */
    suspend fun autoReorderBlocks(schoolId: String, sectorBlockId: String?): List<Block>

    // ── Valoración de vías (estrellas) ──
    /** Vota la vía con [stars] (1-5). Devuelve la media y mi voto resultantes. */
    suspend fun rateLine(blockId: String, lineId: String, stars: Int): LineRating
    /** Retira mi voto de la vía. Devuelve la media resultante. */
    suspend fun unrateLine(blockId: String, lineId: String): LineRating

    // ── Enlaces de beta (Instagram/YouTube) — directo, como comentar ──
    /** Enlaces de la piedra, o de una vía concreta con [lineId]. */
    suspend fun getBetaLinks(blockId: String, lineId: String?): List<BetaLink>
    /** Añade un enlace nuevo (puede haber varios), con categoría de altura opcional. */
    suspend fun addBetaLink(blockId: String, lineId: String?, url: String, heightCategory: String?): BetaLink
    suspend fun deleteBetaLink(linkId: String)

    // ── Comentarios de piedras/vías (con votos de utilidad) ──
    suspend fun getComments(blockId: String): List<LineComment>
    suspend fun addComment(blockId: String, lineId: String?, text: String): LineComment
    /** Vota ±1 (repetir el voto lo retira). Devuelve mi voto resultante. */
    suspend fun voteComment(commentId: String, value: Int): Int
    suspend fun deleteComment(commentId: String)
}

/** Resultado de valorar una vía: media, nº de votos y mi voto. */
data class LineRating(val avgStars: Float, val ratingCount: Long, val myStars: Int)

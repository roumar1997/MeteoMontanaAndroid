package com.meteomontana.android.domain.repository

import com.meteomontana.android.data.api.dto.CreateBlockRequest
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

    // ── Valoración de vías (estrellas) ──
    /** Vota la vía con [stars] (1-5). Devuelve la media y mi voto resultantes. */
    suspend fun rateLine(blockId: String, lineId: String, stars: Int): LineRating
    /** Retira mi voto de la vía. Devuelve la media resultante. */
    suspend fun unrateLine(blockId: String, lineId: String): LineRating

    // ── Comentarios de piedras/vías (con votos de utilidad) ──
    suspend fun getComments(blockId: String): List<LineComment>
    suspend fun addComment(blockId: String, lineId: String?, text: String): LineComment
    /** Vota ±1 (repetir el voto lo retira). Devuelve mi voto resultante. */
    suspend fun voteComment(commentId: String, value: Int): Int
    suspend fun deleteComment(commentId: String)
}

/** Resultado de valorar una vía: media, nº de votos y mi voto. */
data class LineRating(val avgStars: Float, val ratingCount: Long, val myStars: Int)

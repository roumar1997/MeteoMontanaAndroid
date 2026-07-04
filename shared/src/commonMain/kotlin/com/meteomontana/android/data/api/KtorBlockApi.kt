package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class KtorBlockApi(private val client: HttpClient) {

    suspend fun getBlocks(schoolId: String): List<BlockDto> =
        client.get("schools/$schoolId/blocks").body()

    suspend fun getBlock(blockId: String): BlockDto =
        client.get("blocks/$blockId").body()

    suspend fun createBlock(schoolId: String, req: CreateBlockRequest): BlockDto =
        client.post("schools/$schoolId/blocks") { setBody(req) }.body()

    suspend fun updateBlock(blockId: String, req: CreateBlockRequest): BlockDto =
        client.put("blocks/$blockId") { setBody(req) }.body()

    suspend fun deleteBlock(blockId: String) {
        client.delete("blocks/$blockId")
    }

    @Serializable
    data class RatingResult(val avgStars: Float, val ratingCount: Long, val myStars: Int)

    @Serializable
    private data class RateRequest(val stars: Int)

    suspend fun rateLine(blockId: String, lineId: String, stars: Int): RatingResult =
        client.post("blocks/$blockId/lines/$lineId/rate") {
            contentType(ContentType.Application.Json)
            setBody(RateRequest(stars))
        }.body()

    suspend fun unrateLine(blockId: String, lineId: String): RatingResult =
        client.delete("blocks/$blockId/lines/$lineId/rate").body()

    // ── Comentarios de piedras/vías (con votos de utilidad) ──────────────

    /** TODOS los comentarios del bloque (los de vía llevan lineId). */
    suspend fun getComments(blockId: String): List<com.meteomontana.android.data.api.dto.LineCommentDto> =
        client.get("blocks/$blockId/comments").body()

    suspend fun addComment(
        blockId: String,
        req: com.meteomontana.android.data.api.dto.CreateLineCommentRequest
    ): com.meteomontana.android.data.api.dto.LineCommentDto =
        client.post("blocks/$blockId/comments") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    /** Voto ±1 (repetir lo retira). Devuelve el voto vigente. */
    suspend fun voteComment(commentId: String, value: Int): Int {
        val resp: Map<String, Int> = client.post("line-comments/$commentId/vote") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("value" to value))
        }.body()
        return resp["myVote"] ?: 0
    }

    suspend fun deleteComment(commentId: String) {
        client.delete("line-comments/$commentId")
    }
}

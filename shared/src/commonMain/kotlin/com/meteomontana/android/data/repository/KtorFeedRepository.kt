package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorFeedApi
import com.meteomontana.android.data.api.dto.FeedPostDto
import com.meteomontana.android.data.api.dto.PublishFeedRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.FeedComment
import com.meteomontana.android.domain.model.FeedPost
import com.meteomontana.android.domain.repository.FeedRepository
import com.meteomontana.db.MeteoMontanaDb
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Repositorio del feed con CACHÉ DE DISCO de la primera página (por scope):
 * la última página buena se guarda en SQLDelight y, si la red falla al pedir
 * la primera página, se devuelve la cacheada — el feed se ve SIN conexión en
 * Android e iOS sin que las UIs cambien nada (MejorasFuturas, feed 2.14).
 * Solo la primera página (before == null): la paginación sigue siendo red.
 */
class KtorFeedRepository(
    private val api: KtorFeedApi,
    /** null = sin caché (tests o wiring antiguo); el fallback simplemente no actúa. */
    private val db: MeteoMontanaDb? = null
) : FeedRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(FeedPostDto.serializer())

    private fun cacheKey(scope: String, uid: String?) = "$scope|${uid ?: ""}"

    override suspend fun getFeed(scope: String, before: Long?, limit: Int, uid: String?): List<FeedPost> {
        val cache = db
        if (before != null || cache == null) {
            return api.getFeed(scope, before, limit, uid).map { it.toDomain() }
        }
        val page = try {
            api.getFeed(scope, before, limit, uid)
        } catch (t: Throwable) {
            // Sin red: última primera página buena de este scope, si existe.
            val cached = runCatching {
                cache.schemaQueries.selectFeedPage(cacheKey(scope, uid)).executeAsOneOrNull()
            }.getOrNull() ?: throw t
            return runCatching {
                json.decodeFromString(listSerializer, cached.json).map { it.toDomain() }
            }.getOrElse { throw t }
        }
        runCatching {
            cache.schemaQueries.upsertFeedPage(
                cacheKey(scope, uid),
                json.encodeToString(listSerializer, page),
                Clock.System.now().toEpochMilliseconds()
            )
        }
        return page.map { it.toDomain() }
    }

    override suspend fun getPost(postId: Long): FeedPost = api.getPost(postId).toDomain()
    override suspend fun publish(
        blockId: String, lineId: String?, kind: String, discipline: String?, caption: String?
    ): Long =
        api.publish(PublishFeedRequest(blockId, lineId, kind, discipline, caption)).id
    override suspend fun uploadPhoto(postId: Long, bytes: ByteArray, contentType: String): String? =
        api.uploadPhoto(postId, bytes, contentType).photoUrl
    override suspend fun deletePost(postId: Long) = api.deletePost(postId)
    override suspend fun like(postId: Long): Long = api.like(postId).likeCount
    override suspend fun unlike(postId: Long): Long = api.unlike(postId).likeCount
    override suspend fun getComments(postId: Long): List<FeedComment> =
        api.getComments(postId).map { it.toDomain() }
    override suspend fun addComment(postId: Long, text: String, parentId: String?): FeedComment =
        api.addComment(postId, text, parentId).toDomain()
    override suspend fun deleteComment(commentId: String) = api.deleteComment(commentId)
    override suspend fun likeComment(commentId: String): Long = api.likeComment(commentId).likeCount
    override suspend fun unlikeComment(commentId: String): Long = api.unlikeComment(commentId).likeCount
}

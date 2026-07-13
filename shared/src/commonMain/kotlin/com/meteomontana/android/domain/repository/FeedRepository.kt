package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.FeedComment
import com.meteomontana.android.domain.model.FeedPost

interface FeedRepository {
    /** [uid] solo con scope=user (posts de ese usuario; vacío si es privado). */
    suspend fun getFeed(
        scope: String,
        before: Long? = null,
        limit: Int = 20,
        uid: String? = null
    ): List<FeedPost>
    /** Post individual (push/campanita). Lanza si no existe (404). */
    suspend fun getPost(postId: Long): FeedPost
    /** Publica un ascenso; devuelve el id del post creado (idempotente en el server).
     *  Ids del backend = String (UUID), se mandan tal cual. */
    suspend fun publish(blockId: String, lineId: String?, kind: String, discipline: String? = null): Long
    suspend fun deletePost(postId: Long)
    /** Devuelve el likeCount actualizado. */
    suspend fun like(postId: Long): Long
    suspend fun unlike(postId: Long): Long
    suspend fun getComments(postId: Long): List<FeedComment>
    suspend fun addComment(postId: Long, text: String): FeedComment
    suspend fun deleteComment(commentId: String)
}

package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorFeedApi
import com.meteomontana.android.data.api.dto.PublishFeedRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.FeedComment
import com.meteomontana.android.domain.model.FeedPost
import com.meteomontana.android.domain.repository.FeedRepository

class KtorFeedRepository(private val api: KtorFeedApi) : FeedRepository {
    override suspend fun getFeed(scope: String, before: Long?, limit: Int, uid: String?): List<FeedPost> =
        api.getFeed(scope, before, limit, uid).map { it.toDomain() }
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
    override suspend fun addComment(postId: Long, text: String): FeedComment =
        api.addComment(postId, text).toDomain()
    override suspend fun deleteComment(commentId: String) = api.deleteComment(commentId)
}

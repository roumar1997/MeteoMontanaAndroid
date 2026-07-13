package com.meteomontana.android.data.api

import com.meteomontana.android.data.api.dto.AddFeedCommentRequest
import com.meteomontana.android.data.api.dto.FeedCommentDto
import com.meteomontana.android.data.api.dto.FeedLikeCountDto
import com.meteomontana.android.data.api.dto.FeedPostDto
import com.meteomontana.android.data.api.dto.FeedPostIdDto
import com.meteomontana.android.data.api.dto.PublishFeedRequest
import com.meteomontana.android.data.api.dto.FeedPhotoUrlDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/** Feed social "Comunidad" (todo con Bearer). Patrón exacto de KtorSocialApi. */
class KtorFeedApi(private val client: HttpClient) {

    /**
     * Página del feed. [scope] = "following" | "all" | "mine" | "user";
     * [before] = id del último post recibido (cursor hacia lo antiguo), null =
     * primera página. [uid] solo con scope=user (posts de ese usuario; el
     * backend devuelve lista vacía si es privado y no le sigues, nunca 404).
     */
    suspend fun getFeed(
        scope: String,
        before: Long? = null,
        limit: Int = 20,
        uid: String? = null
    ): List<FeedPostDto> =
        client.get("feed") {
            parameter("scope", scope)
            if (before != null) parameter("before", before)
            parameter("limit", limit)
            if (uid != null) parameter("uid", uid)
        }.body()

    /** Post individual (deep link de push/campanita). 404 si no existe/no visible. */
    suspend fun getPost(postId: Long): FeedPostDto =
        client.get("feed/$postId").body()

    suspend fun publish(request: PublishFeedRequest): FeedPostIdDto =
        client.post("feed") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /**
     * Sube la foto de celebración de un post propio (multipart, campo "file",
     * JPEG máx 5MB). Devuelve la URL firmada de la foto.
     */
    suspend fun uploadPhoto(
        postId: Long,
        bytes: ByteArray,
        contentType: String = "image/jpeg"
    ): FeedPhotoUrlDto =
        client.submitFormWithBinaryData(
            url = "feed/$postId/photo",
            formData = formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"celebration.jpg\"")
                })
            }
        ).body()

    suspend fun deletePost(postId: Long) { client.delete("feed/$postId") }

    suspend fun like(postId: Long): FeedLikeCountDto =
        client.post("feed/$postId/like").body()

    suspend fun unlike(postId: Long): FeedLikeCountDto =
        client.delete("feed/$postId/like").body()

    suspend fun getComments(postId: Long): List<FeedCommentDto> =
        client.get("feed/$postId/comments").body()

    suspend fun addComment(postId: Long, text: String): FeedCommentDto =
        client.post("feed/$postId/comments") {
            contentType(ContentType.Application.Json)
            setBody(AddFeedCommentRequest(text))
        }.body()

    suspend fun deleteComment(commentId: String) {
        client.delete("feed/comments/$commentId")
    }
}

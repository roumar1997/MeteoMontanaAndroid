package com.meteomontana.android.domain.usecase.feed

import com.meteomontana.android.domain.model.FeedComment
import com.meteomontana.android.domain.model.FeedPost
import com.meteomontana.android.domain.repository.FeedRepository

/** Ámbitos del feed que entiende el backend. */
object FeedScope {
    const val FOLLOWING = "following"
    const val ALL = "all"
    /** Solo mis publicaciones (chip MÍAS). */
    const val MINE = "mine"
    /** Posts de un usuario concreto (perfil público); requiere uid. */
    const val USER = "user"
}

/** Tipos de post (kind) del feed. */
object FeedKind {
    const val TICK = "TICK"
    const val PROJECT_DONE = "PROJECT_DONE"
    const val NEW_BLOCK = "NEW_BLOCK"
    const val NEW_LINE = "NEW_LINE"
}

/** Página del feed; [before] = id del último post (cursor hacia lo antiguo).
 *  [uid] solo con scope=user (posts de ese usuario; vacío si es privado). */
class GetFeedPageUseCase(private val repo: FeedRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        scope: String,
        before: Long? = null,
        limit: Int = 20,
        uid: String? = null
    ): List<FeedPost> = repo.getFeed(scope, before, limit, uid)
}

/** Post individual (push/campanita). Lanza si no existe (404). */
class GetFeedPostUseCase(private val repo: FeedRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: Long): FeedPost = repo.getPost(postId)
}

/** Publica un ascenso (TICK / PROJECT_DONE). Ids String (UUID) tal cual;
 *  [discipline] = "BOULDER" | "ROUTE" (modalidad de la piedra), opcional;
 *  [caption] = descripción del autor (opcional, max 500). */
class PublishFeedPostUseCase(private val repo: FeedRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        blockId: String,
        lineId: String?,
        kind: String,
        discipline: String? = null,
        caption: String? = null
    ): Long = repo.publish(blockId, lineId, kind, discipline, caption)
}

/** Sube la foto de celebración (JPEG comprimido) de un post propio.
 *  Devuelve la URL firmada de la foto (o null si el backend no la manda). */
class UploadFeedPhotoUseCase(private val repo: FeedRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        postId: Long,
        bytes: ByteArray,
        contentType: String = "image/jpeg"
    ): String? = repo.uploadPhoto(postId, bytes, contentType)
}

class DeleteFeedPostUseCase(private val repo: FeedRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: Long) = repo.deletePost(postId)
}

/** Da like a un post; devuelve el likeCount actualizado. */
class LikeFeedPostUseCase(private val repo: FeedRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: Long): Long = repo.like(postId)
}

/** Quita el like; devuelve el likeCount actualizado. */
class UnlikeFeedPostUseCase(private val repo: FeedRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: Long): Long = repo.unlike(postId)
}

class GetFeedCommentsUseCase(private val repo: FeedRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: Long): List<FeedComment> = repo.getComments(postId)
}

class AddFeedCommentUseCase(private val repo: FeedRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: Long, text: String): FeedComment =
        repo.addComment(postId, text)
}

class DeleteFeedCommentUseCase(private val repo: FeedRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(commentId: String) = repo.deleteComment(commentId)
}

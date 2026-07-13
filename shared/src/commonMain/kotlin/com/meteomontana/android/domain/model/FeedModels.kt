package com.meteomontana.android.domain.model

/** Autor de un post/comentario del feed Comunidad. */
data class FeedAuthor(
    val uid: String,
    val username: String?,
    val displayName: String?,
    val photoUrl: String?
)

/**
 * Post del feed social Comunidad. La imagen NO viaja: se compone en cliente
 * con [photoPath] (foto de la cara) + [linePath] (trazo de la vía), igual que
 * el detalle de piedra.
 */
data class FeedPost(
    val id: Long,
    /** TICK | PROJECT_DONE | NEW_BLOCK | NEW_LINE */
    val kind: String,
    val createdAt: String,
    val author: FeedAuthor,
    val schoolId: String?,
    val schoolName: String?,
    val blockId: String?,
    val blockName: String?,
    val lineId: String?,
    val lineName: String?,
    val grade: String?,
    /** "BOULDER" | "ROUTE" | null (posts anteriores al campo). */
    val discipline: String?,
    /** Tipo de roca de la escuela o null. */
    val rockType: String?,
    val photoPath: String?,
    val linePath: String?,
    val likeCount: Long,
    val likedByMe: Boolean,
    val commentCount: Long,
    val mine: Boolean,
    /** "SIT" | "STAND" | "JUMP" | "TRAV" | null (tipo de inicio de la vía, en vivo). */
    val startType: String? = null,
    /** Descripción del autor (max 500) o null. */
    val caption: String? = null,
    /** URL firmada de la foto de celebración o null. */
    val photoUrl: String? = null
)

data class FeedComment(
    val id: String,
    val postId: Long,
    val uid: String?,
    val author: FeedAuthor?,
    val text: String,
    val createdAt: String,
    val mine: Boolean
)

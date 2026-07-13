package com.meteomontana.android.data.api.dto

import com.meteomontana.android.domain.model.FeedAuthor
import com.meteomontana.android.domain.model.FeedComment
import com.meteomontana.android.domain.model.FeedPost
import kotlinx.serialization.Serializable

@Serializable
data class FeedAuthorDto(
    val uid: String,
    val username: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null
)

@Serializable
data class FeedPostDto(
    val id: Long,
    val kind: String,
    val createdAt: String,
    val author: FeedAuthorDto,
    // ids como String: el cliente solo los reenvía en rutas/navegación
    // (isLenient acepta que el backend los serialice como número).
    val schoolId: String? = null,
    val schoolName: String? = null,
    val blockId: String? = null,
    val blockName: String? = null,
    val lineId: String? = null,
    val lineName: String? = null,
    val grade: String? = null,
    /** "BOULDER" | "ROUTE" | null (posts anteriores al campo). */
    val discipline: String? = null,
    /** Tipo de roca de la escuela (Granito, Caliza…) o null. */
    val rockType: String? = null,
    val photoPath: String? = null,
    val linePath: String? = null,
    val likeCount: Long = 0,
    val likedByMe: Boolean = false,
    val commentCount: Long = 0,
    val mine: Boolean = false
)

@Serializable
data class FeedCommentDto(
    val id: String,
    val postId: Long = 0,
    val uid: String? = null,
    val author: FeedAuthorDto? = null,
    val text: String,
    val createdAt: String,
    val mine: Boolean = false
)

/**
 * POST /api/feed — publicar un ascenso (el server snapshotea grado/nombre).
 * OJO: los ids del backend son VARCHAR (UUID) — se mandan como String tal
 * cual, sin conversión numérica (convertirlos con toLongOrNull hacía que
 * NUNCA se publicara nada).
 */
@Serializable
data class PublishFeedRequest(
    val blockId: String,
    val lineId: String? = null,
    val kind: String,
    /** "BOULDER" | "ROUTE" (modalidad de la piedra); opcional. */
    val discipline: String? = null
)

/** POST /api/feed/{id}/comments */
@Serializable
data class AddFeedCommentRequest(val text: String)

@Serializable
data class FeedPostIdDto(val id: Long)

@Serializable
data class FeedLikeCountDto(val likeCount: Long)

// ── Mapping DTO → dominio ────────────────────────────────────────────────────

fun FeedAuthorDto.toDomain() = FeedAuthor(
    uid = uid, username = username, displayName = displayName, photoUrl = photoUrl
)

fun FeedPostDto.toDomain() = FeedPost(
    id = id, kind = kind, createdAt = createdAt, author = author.toDomain(),
    schoolId = schoolId, schoolName = schoolName,
    blockId = blockId, blockName = blockName,
    lineId = lineId, lineName = lineName, grade = grade,
    discipline = discipline, rockType = rockType,
    photoPath = photoPath, linePath = linePath,
    likeCount = likeCount, likedByMe = likedByMe,
    commentCount = commentCount, mine = mine
)

fun FeedCommentDto.toDomain() = FeedComment(
    id = id, postId = postId, uid = uid, author = author?.toDomain(),
    text = text, createdAt = createdAt, mine = mine
)

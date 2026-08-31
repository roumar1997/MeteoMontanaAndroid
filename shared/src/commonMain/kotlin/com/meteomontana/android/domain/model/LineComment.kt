package com.meteomontana.android.domain.model

/**
 * Comentario de la comunidad en una piedra/muro (lineId=null) o en una vía
 * concreta, con votos de utilidad. Modelo de dominio: lo que ven la UI y los
 * use cases (el DTO del backend se queda en la capa de datos).
 */
data class LineComment(
    val id: String,
    val blockId: String,
    val lineId: String?,
    val author: String,
    val uid: String,
    val createdAt: String?,
    val text: String,
    val upvotesCount: Int,
    val downvotesCount: Int,
    /** Mi voto: 1, -1 o 0. */
    val myVote: Int
)

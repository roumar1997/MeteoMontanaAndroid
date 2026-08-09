package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class BlockDto(
    val id: String,
    val schoolId: String,
    val type: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val photoPath: String? = null,
    val description: String? = null,
    val createdByUid: String,
    val createdAt: String,
    val lines: List<BlockLineDto> = emptyList(),
    val sectorBlockId: String? = null,
    val discipline: String = "BOULDER",   // BOULDER (bloque) / ROUTE (vía)
    val geometry: String = "POINT",       // POINT / LINE (muro)
    val path: String? = null,             // polilínea JSON si LINE
    val direction: String = "LTR",        // "LTR"/"RTL"
    // Caras = la piedra agrupada por foto (cada cara: foto + sus vías).
    val faces: List<BlockFaceDto> = emptyList()
)

@Serializable
data class BlockLineDto(
    val id: String,
    val name: String,
    val grade: String? = null,
    val startType: String? = null,
    val linePath: String? = null,
    val sortOrder: Int,
    val photoPath: String? = null,
    val faceOrder: Int = 0,
    val avgStars: Float? = null,
    val myStars: Int? = null,
    val description: String? = null,
    // Variante opcional ("directa", "extensión"...) — distingue vías homónimas.
    val variant: String? = null
)

@Serializable
data class BlockFaceDto(
    val photoPath: String? = null,
    val sortOrder: Int = 0,
    val lines: List<BlockLineDto> = emptyList()
)

@Serializable
data class CreateBlockLineRequest(
    /**
     * Id de la via EXISTENTE que representa esta fila; null si es nueva.
     *
     * Del id de una via cuelgan el ✓ del diario de cada usuario, sus estrellas,
     * los votos de grado, los comentarios y los enlaces compartidos. Sin este
     * campo, el servidor tiene que emparejar por nombre y posicion, y en un
     * guardado que borra una via y anade otra puede equivocarse: eso mueve el
     * ✓ de alguien a otra via. Mandandolo, no hay nada que adivinar.
     *
     * Es opcional a proposito: las apps viejas no lo mandan y el servidor
     * sigue emparejando como hasta ahora.
     */
    val id: String? = null,
    val name: String,
    val grade: String? = null,
    val startType: String? = null,
    val linePath: String? = null,
    val photoPath: String? = null,
    val faceOrder: Int = 0,
    val description: String? = null,
    val variant: String? = null
)

/** Comentario de la comunidad en una piedra (lineId=null) o en una vía. */
@Serializable
data class LineCommentDto(
    val id: String,
    val blockId: String,
    val lineId: String? = null,
    val author: String,
    val uid: String,
    val createdAt: String? = null,
    val text: String,
    val upvotesCount: Int = 0,
    val downvotesCount: Int = 0,
    val myVote: Int = 0
)

@Serializable
data class CreateLineCommentRequest(
    val lineId: String? = null,
    val text: String
)

@Serializable
data class CreateBlockRequest(
    val type: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val photoPath: String? = null,
    val description: String? = null,
    val lines: List<CreateBlockLineRequest> = emptyList(),
    val sectorBlockId: String? = null,
    val discipline: String? = null,  // BLOCK: BOULDER (bloque) / ROUTE (vía)
    val geometry: String? = null,    // BLOCK: POINT / LINE (muro)
    val path: String? = null,        // BLOCK+LINE: polilínea JSON
    val direction: String? = null    // BLOCK+LINE: "LTR"/"RTL"
)

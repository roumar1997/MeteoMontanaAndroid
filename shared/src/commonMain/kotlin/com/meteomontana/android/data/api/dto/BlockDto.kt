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
    val faceOrder: Int = 0
)

@Serializable
data class BlockFaceDto(
    val photoPath: String? = null,
    val sortOrder: Int = 0,
    val lines: List<BlockLineDto> = emptyList()
)

@Serializable
data class CreateBlockLineRequest(
    val name: String,
    val grade: String? = null,
    val startType: String? = null,
    val linePath: String? = null,
    val photoPath: String? = null,
    val faceOrder: Int = 0
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
    val discipline: String? = null   // BLOCK: BOULDER (bloque) / ROUTE (vía)
)

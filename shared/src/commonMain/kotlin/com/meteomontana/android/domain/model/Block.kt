package com.meteomontana.android.domain.model

data class Block(
    val id: String,
    val schoolId: String,
    val type: String,                // BLOCK / PARKING / ZONE
    val name: String,
    val lat: Double,
    val lon: Double,
    val photoPath: String?,
    val description: String?,
    val createdByUid: String,
    val createdAt: String,
    val lines: List<BlockLine>,
    val sectorBlockId: String? = null  // BLOCK: id del sector (ZONE) al que pertenece
) {
    // Alias para Swift: en iOS `block.description` lo intercepta NSObjectProtocol
    // (devuelve el debug string del objeto, no esta propiedad). SKIE exporta este
    // accesor con nombre propio, así que iOS lee la descripción real vía
    // `descriptionText`. Android sigue usando `description` con normalidad.
    val descriptionText: String? get() = description
}

data class BlockLine(
    val id: String,
    val name: String,
    val grade: String?,
    val startType: String?,
    val linePath: String?,
    val sortOrder: Int
)

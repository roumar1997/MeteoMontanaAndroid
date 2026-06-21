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
    val sectorBlockId: String? = null,  // BLOCK: id del sector (ZONE) al que pertenece
    // Modalidad de la piedra: "BOULDER" (bloque) o "ROUTE" (vía). Default BOULDER.
    val discipline: String = "BOULDER",
    // Geometría en el mapa: "POINT" (marcador) o "LINE" (muro = polilínea).
    val geometry: String = "POINT",
    // Polilínea (base del muro) JSON "[[lat,lon],...]" si geometry=LINE; null si POINT.
    val path: String? = null,
    // Sentido de numeración de las vías del muro: "LTR" / "RTL".
    val direction: String = "LTR",
    // Caras de la piedra: cada cara es una foto + las vías dibujadas sobre ella.
    // Una piedra de una sola foto tiene una única cara. Si viene vacío, los
    // consumidores caen a (photoPath + lines) como cara única.
    val faces: List<BlockFace> = emptyList()
) {
    /**
     * Caras de la piedra para pintar. Si `faces` viene (online) se usa tal cual;
     * si no (p.ej. offline, donde el bloque se reconstruye sin caras) se derivan
     * agrupando las vías por su foto, con la portada como respaldo. Una piedra de
     * una sola foto da una única cara.
     */
    fun facesOrDerived(): List<BlockFace> {
        if (faces.isNotEmpty()) return faces
        if (lines.isEmpty()) {
            return if (photoPath != null) listOf(BlockFace(photoPath, 0, emptyList())) else emptyList()
        }
        val byPhoto = LinkedHashMap<String, MutableList<BlockLine>>()
        for (l in lines) {
            val key = l.photoPath ?: photoPath ?: ""
            byPhoto.getOrPut(key) { mutableListOf() }.add(l)
        }
        return byPhoto.entries.mapIndexed { idx, e ->
            BlockFace(if (e.key.isEmpty()) null else e.key, idx, e.value)
        }
    }

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
    val sortOrder: Int,
    val photoPath: String? = null,  // foto (cara) sobre la que está dibujada
    val faceOrder: Int = 0          // orden de su cara dentro de la piedra
)

/** Una cara de la piedra: una foto y las vías dibujadas sobre ella. */
data class BlockFace(
    val photoPath: String?,
    val sortOrder: Int,
    val lines: List<BlockLine>
)

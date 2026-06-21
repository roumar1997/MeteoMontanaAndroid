package com.meteomontana.android.ui.screens.detail

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import com.meteomontana.android.ui.screens.topo.LineStroke
import com.meteomontana.android.ui.screens.topo.toJson
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Representa un bloque/vía dentro de una propuesta de piedra (estado UI, no DTO). */
data class BoulderBloqueForm(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val grade: String? = null,       // "6a", "7b+", "PROY", etc.
    val startType: String? = null,   // "PIE" | "SIT" | "LANCE" | "TRAV"
    val linePath: List<Offset> = emptyList(),
    // Foto (cara) a la que pertenece esta vía. Al corregir/añadir en una piedra
    // multi-foto, mantiene la vía en SU cara (no la mueve a la portada).
    val facePhoto: String? = null,
    // id de la vía existente que representa esta fila (null = vía nueva). Permite
    // corregir VARIAS vías existentes en una sola propuesta (el backend las
    // distingue por este targetLineId por nodo).
    val existingLineId: String? = null
)

/**
 * Una CARA de la piedra al proponer: una foto y las vías dibujadas sobre ella.
 * Una piedra grande no cabe en una foto → se proponen varias caras (foto→vías,
 * foto→vías). La foto es local (Uri); se sube al enviar.
 */
data class BoulderFaceForm(
    val id: String = UUID.randomUUID().toString(),
    val photoUri: Uri? = null,
    val bloques: List<BoulderBloqueForm> = listOf(BoulderBloqueForm())
)

/** Serializa la polilínea del muro a JSON "[[lat,lon],...]" (formato de `Block.path`). */
fun List<Pair<Double, Double>>.toPathJson(): String {
    val arr = JSONArray()
    forEach { (lat, lon) ->
        arr.put(JSONArray().put(lat).put(lon))
    }
    return arr.toString()
}

val BOULDER_GRADES = listOf(
    "3", "4", "5", "5+",
    "6a", "6a+", "6b", "6b+", "6c", "6c+",
    "7a", "7a+", "7b", "7b+", "7c", "7c+",
    "8a", "8a+", "8b", "8b+", "8c", "8c+",
    "9a", "PROY"
)

/** Serializa la lista de bloques para enviar al backend. */
fun List<BoulderBloqueForm>.toBloquesJson(): String {
    val arr = JSONArray()
    forEachIndexed { idx, b ->
        // Cada vía conserva la foto (cara) a la que pertenece.
        arr.put(bloqueJson(idx, b, b.facePhoto))
    }
    return arr.toString()
}

/**
 * Serializa varias CARAS (foto + sus vías) a un único `bloquesJson` donde cada vía
 * lleva el `photoUrl` de su cara. El backend agrupa por foto en caras según el
 * orden de aparición. `photoUrlByFace` = URL ya subida de cada cara (por id).
 */
fun facesToBloquesJson(
    faces: List<BoulderFaceForm>,
    photoUrlByFace: Map<String, String?>
): String {
    val arr = JSONArray()
    var idx = 0
    faces.forEach { face ->
        val facePhoto = photoUrlByFace[face.id]
        face.bloques.forEach { b ->
            arr.put(bloqueJson(idx++, b, facePhoto))
        }
    }
    return arr.toString()
}

private fun bloqueJson(idx: Int, b: BoulderBloqueForm, photoUrl: String?): JSONObject =
    JSONObject().apply {
        put("idx", idx)
        put("name", b.name)
        if (b.grade != null) put("grade", b.grade) else put("grade", JSONObject.NULL)
        if (b.startType != null) put("startType", b.startType) else put("startType", JSONObject.NULL)
        put("linePath", LineStroke(b.linePath).toJson())
        if (photoUrl != null) put("photoUrl", photoUrl) else put("photoUrl", JSONObject.NULL)
        // Si la fila es una vía existente, el backend la CORRIGE (no añade).
        if (b.existingLineId != null) put("targetLineId", b.existingLineId) else put("targetLineId", JSONObject.NULL)
    }

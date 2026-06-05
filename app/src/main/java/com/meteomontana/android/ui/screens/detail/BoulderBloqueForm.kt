package com.meteomontana.android.ui.screens.detail

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
    val linePath: List<Offset> = emptyList()
)

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
        arr.put(JSONObject().apply {
            put("idx", idx)
            put("name", b.name)
            if (b.grade != null) put("grade", b.grade) else put("grade", JSONObject.NULL)
            if (b.startType != null) put("startType", b.startType) else put("startType", JSONObject.NULL)
            put("linePath", LineStroke(b.linePath).toJson())
        })
    }
    return arr.toString()
}

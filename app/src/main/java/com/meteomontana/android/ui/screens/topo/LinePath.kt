package com.meteomontana.android.ui.screens.topo

import androidx.compose.ui.geometry.Offset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Representa la trayectoria de una línea sobre la foto.
 * Puntos normalizados en [0..1] sobre las dimensiones de la imagen.
 */
data class LineStroke(val points: List<Offset>)

/** Serializa a JSON: `[{x:0.1,y:0.2},{x:0.3,y:0.4}]` */
fun LineStroke.toJson(): String {
    val arr = JSONArray()
    points.forEach { p ->
        arr.put(JSONObject().apply {
            put("x", p.x)
            put("y", p.y)
        })
    }
    return arr.toString()
}

fun parseLineStroke(json: String?): LineStroke {
    if (json.isNullOrBlank()) return LineStroke(emptyList())
    return try {
        val arr = JSONArray(json)
        val pts = mutableListOf<Offset>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            pts.add(Offset(o.getDouble("x").toFloat(), o.getDouble("y").toFloat()))
        }
        LineStroke(pts)
    } catch (_: Throwable) {
        LineStroke(emptyList())
    }
}

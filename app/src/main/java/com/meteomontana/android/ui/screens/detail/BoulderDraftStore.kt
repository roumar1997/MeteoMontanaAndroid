package com.meteomontana.android.ui.screens.detail

import android.content.Context
import androidx.compose.ui.geometry.Offset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Piedras a medias: lo que llevabas escrito cuando cerraste el formulario.
 *
 * Proponer una piedra es largo —nombre, modalidad, orientación, una foto por
 * cara y una línea dibujada por vía—, y hasta ahora cerrar por error, recibir
 * una llamada o quedarte sin batería significaba empezar de cero. Eso hace que
 * la gente no lo intente en el sitio, que es justo cuando tiene la roca
 * delante.
 *
 * Se guarda **solo en este móvil**, nunca en el servidor: es tuyo, está a
 * medias y no tiene por qué verlo un admin hasta que lo envíes.
 *
 * Un borrador por escuela: lo normal es abrir varias piedras en la misma
 * jornada y en el mismo sitio, y así el aviso de "tienes una a medias" aparece
 * donde tiene sentido.
 *
 * No se inyecta ni vive en el ViewModel: es estado de una pantalla que solo
 * necesita `Context`. Meterlo en el ViewModel obligaba a que sus tests
 * fabricaran uno —y los rompio— por algo que no tiene nada que ver con ellos.
 *
 * LÍMITE CONOCIDO: las fotos se guardan como `Uri`, que es un puntero a la
 * galería, no una copia. Si el usuario borra la foto del móvil, el borrador
 * conserva el texto y las líneas pero pierde la imagen. Copiarlas a la app
 * costaría espacio en disco por algo que casi siempre se termina el mismo día;
 * si algún día molesta, este es el sitio donde cambiarlo.
 */
class BoulderDraftStore(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("cumbre_borradores_piedra", Context.MODE_PRIVATE)
    }

    /** Todo lo que hace falta para dejar el formulario como estaba. */
    data class Draft(
        val schoolId: String,
        val lat: Double,
        val lon: Double,
        val name: String,
        val discipline: String,
        val geometry: String,
        val direction: String,
        val sectorBlockId: String?,
        val orientation: String?,
        val path: List<Pair<Double, Double>>,
        val faces: List<BoulderFaceForm>,
        /** Cuándo se guardó, para poder decir "hace 2 días". */
        val savedAt: Long
    )

    fun save(draft: Draft) {
        prefs.edit().putString(draft.schoolId, aJson(draft).toString()).apply()
    }

    fun load(schoolId: String): Draft? =
        prefs.getString(schoolId, null)?.let {
            runCatching { deJson(JSONObject(it)) }.getOrNull()
        }

    fun clear(schoolId: String) {
        prefs.edit().remove(schoolId).apply()
    }

    // ── Serialización ───────────────────────────────────────────────────────
    // A mano y no con una librería porque el formulario es estado de UI, no un
    // DTO del servidor: si mañana cambia un campo, lo que NO puede pasar es que
    // reviente al leer un borrador viejo. De ahí que `load` devuelva null ante
    // cualquier problema en vez de propagar el error.

    private fun aJson(d: Draft) = JSONObject().apply {
        put("schoolId", d.schoolId)
        put("lat", d.lat); put("lon", d.lon)
        put("name", d.name)
        put("discipline", d.discipline)
        put("geometry", d.geometry)
        put("direction", d.direction)
        put("sectorBlockId", d.sectorBlockId ?: JSONObject.NULL)
        put("orientation", d.orientation ?: JSONObject.NULL)
        put("savedAt", d.savedAt)
        put("path", JSONArray().apply {
            d.path.forEach { put(JSONArray().put(it.first).put(it.second)) }
        })
        put("faces", JSONArray().apply {
            d.faces.forEach { cara ->
                put(JSONObject().apply {
                    put("photoUri", cara.photoUri?.toString() ?: JSONObject.NULL)
                    put("orientation", cara.orientation ?: JSONObject.NULL)
                    put("bloques", JSONArray().apply {
                        cara.bloques.forEach { b ->
                            put(JSONObject().apply {
                                put("name", b.name)
                                put("grade", b.grade ?: JSONObject.NULL)
                                put("startType", b.startType ?: JSONObject.NULL)
                                put("description", b.description ?: JSONObject.NULL)
                                put("variant", b.variant ?: JSONObject.NULL)
                                put("linePath", JSONArray().apply {
                                    b.linePath.forEach { p -> put(JSONArray().put(p.x).put(p.y)) }
                                })
                            })
                        }
                    })
                })
            }
        })
    }

    private fun deJson(o: JSONObject): Draft {
        val path = o.optJSONArray("path")?.let { arr ->
            (0 until arr.length()).map {
                val p = arr.getJSONArray(it); p.getDouble(0) to p.getDouble(1)
            }
        } ?: emptyList()

        val caras = o.optJSONArray("faces")?.let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                BoulderFaceForm(
                    photoUri = c.optString("photoUri", "").takeIf { it.isNotBlank() }
                        ?.let { android.net.Uri.parse(it) },
                    orientation = c.optString("orientation", "").takeIf { it.isNotBlank() },
                    bloques = c.optJSONArray("bloques")?.let { bs ->
                        (0 until bs.length()).map { j ->
                            val b = bs.getJSONObject(j)
                            BoulderBloqueForm(
                                name = b.optString("name", ""),
                                grade = b.optString("grade", "").takeIf { it.isNotBlank() },
                                startType = b.optString("startType", "").takeIf { it.isNotBlank() },
                                description = b.optString("description", "").takeIf { it.isNotBlank() },
                                variant = b.optString("variant", "").takeIf { it.isNotBlank() },
                                linePath = b.optJSONArray("linePath")?.let { ps ->
                                    (0 until ps.length()).map { k ->
                                        val p = ps.getJSONArray(k)
                                        Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                                    }
                                } ?: emptyList()
                            )
                        }
                    } ?: listOf(BoulderBloqueForm())
                )
            }
        } ?: listOf(BoulderFaceForm())

        return Draft(
            schoolId = o.getString("schoolId"),
            lat = o.getDouble("lat"), lon = o.getDouble("lon"),
            name = o.optString("name", ""),
            discipline = o.optString("discipline", "BOULDER"),
            geometry = o.optString("geometry", "POINT"),
            direction = o.optString("direction", "LTR"),
            sectorBlockId = o.optString("sectorBlockId", "").takeIf { it.isNotBlank() },
            orientation = o.optString("orientation", "").takeIf { it.isNotBlank() },
            path = path,
            faces = caras,
            savedAt = o.optLong("savedAt", 0L)
        )
    }
}

/** ¿Tiene el borrador algo que merezca la pena guardar? */
fun BoulderDraftStore.Draft.tieneContenido(): Boolean =
    name.isNotBlank() ||
        faces.any { cara ->
            cara.photoUri != null ||
                cara.bloques.any { it.name.isNotBlank() || it.grade != null || it.linePath.isNotEmpty() }
        }

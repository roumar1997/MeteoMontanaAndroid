package com.meteomontana.android.ui.screens.detail

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Ediciones de una piedra YA EXISTENTE a medias: lo que llevabas cambiado
 * cuando cerraste el editor sin enviar. Espejo Android de
 * `EditBlockDraftStore.swift` — clave por blockId, no por schoolId (puede
 * haber varias piedras con un cambio a medias a la vez).
 *
 * Petición de Rodrigo (2026-08-21): "desde editar una piedra también te
 * deja darle a guardar y terminar luego?" — ya existía al CREAR una piedra
 * nueva (`BoulderDraftStore`), faltaba al EDITAR una existente.
 *
 * Las fotos nuevas se copian a disco AL GUARDAR el borrador (no basta con
 * guardar el Uri: el selector de fotos del sistema solo garantiza el permiso
 * de lectura mientras vive el proceso, no entre reinicios de la app).
 */
internal object EditBlockDraftStore {

    data class Draft(
        val blockId: String,
        val faces: List<EditFace>,
        val savedAt: Long
    )

    private const val CARPETA = "borradores-edicion-piedra"

    private fun carpeta(context: Context): File =
        File(context.filesDir, CARPETA).apply { mkdirs() }

    private fun seguro(s: String): String = s.map { if (it.isLetterOrDigit() || it == '-') it else '_' }.joinToString("")

    private fun fichero(context: Context, blockId: String): File =
        File(carpeta(context), "${seguro(blockId)}.json")

    /**
     * Copia la foto elegida a la carpeta de la app y devuelve SU uri.
     *
     * Se llama al ELEGIRLA, no al guardar: el Uri que da el selector del
     * sistema solo se puede leer mientras vive el proceso, así que guardarlo
     * tal cual significa perder la foto en cuanto el móvil mate la app.
     * null si no se pudo copiar (y entonces se usa el Uri original).
     */
    fun copiarFotoLocal(context: Context, uri: Uri): Uri? = runCatching {
        val destino = File(carpeta(context), "elegida-${System.currentTimeMillis()}.jpg")
        val entrada = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        entrada.use { e -> destino.outputStream().use { e.copyTo(it) } }
        if (destino.length() > 0L) destino.toUri() else null
    }.getOrNull().also {
        if (it == null) android.util.Log.w("Cumbre", "No se pudo copiar la foto elegida: $uri")
    }

    fun save(context: Context, blockId: String, faces: List<EditFace>) {
        val facesJson = JSONArray()
        faces.forEachIndexed { i, face ->
            val obj = JSONObject()
            obj.put("existingPhotoPath", face.existingPhotoPath ?: JSONObject.NULL)
            // Foto nueva local -> copia propia (sobrevive a reinicios).
            val fotoLocal = face.newPhotoUri?.let { uri ->
                runCatching {
                    val nombre = "${seguro(blockId)}-cara$i.jpg"
                    val destino = File(carpeta(context), nombre)
                    val entrada = context.contentResolver.openInputStream(uri)
                    // Si el selector ya no da permiso de lectura, openInputStream
                    // devuelve NULL: antes el `?.` se saltaba la copia y aun así
                    // se guardaba el nombre, así que el borrador apuntaba a un
                    // fichero que no existía → al continuar salía "FOTO NUEVA"
                    // pero sin imagen, y sin poder dibujar (Álvaro, 2026-08-24).
                    // Ahora solo se apunta la foto si de verdad se copió.
                    if (entrada == null) {
                        android.util.Log.w("Cumbre",
                            "Borrador cara $i: sin permiso para leer la foto elegida ($uri)")
                        return@runCatching null
                    }
                    entrada.use { e -> destino.outputStream().use { e.copyTo(it) } }
                    if (destino.length() > 0L) nombre else null
                }.getOrElse { ex ->
                    android.util.Log.w("Cumbre", "Borrador cara $i: fallo copiando $uri", ex)
                    null
                }
            }
            obj.put("photoFile", fotoLocal ?: JSONObject.NULL)
            val bloquesArr = JSONArray()
            face.bloques.forEach { b ->
                val bo = JSONObject()
                bo.put("id", b.id)
                bo.put("name", b.name)
                bo.put("grade", b.grade ?: JSONObject.NULL)
                bo.put("startType", b.startType ?: JSONObject.NULL)
                bo.put("facePhoto", b.facePhoto ?: JSONObject.NULL)
                bo.put("existingLineId", b.existingLineId ?: JSONObject.NULL)
                bo.put("description", b.description ?: JSONObject.NULL)
                bo.put("variant", b.variant ?: JSONObject.NULL)
                val pathArr = JSONArray()
                b.linePath.forEach { p -> pathArr.put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble())) }
                bo.put("linePath", pathArr)
                bloquesArr.put(bo)
            }
            obj.put("bloques", bloquesArr)
            facesJson.put(obj)
        }
        val raiz = JSONObject()
        raiz.put("blockId", blockId)
        raiz.put("faces", facesJson)
        raiz.put("savedAt", System.currentTimeMillis())
        runCatching { fichero(context, blockId).writeText(raiz.toString()) }
    }

    /** null ante cualquier problema: un borrador roto no puede bloquear editar. */
    fun load(context: Context, blockId: String): Draft? = runCatching {
        val texto = fichero(context, blockId).takeIf { it.exists() }?.readText() ?: return null
        val raiz = JSONObject(texto)
        val facesArr = raiz.getJSONArray("faces")
        val faces = (0 until facesArr.length()).map { i ->
            val obj = facesArr.getJSONObject(i)
            // textoONull y NO optString a secas: con un JSON null, optString
            // devuelve la CADENA "null" (no vacía), así que el filtro de
            // isNotBlank la dejaba pasar y se construía la ruta
            // ".../borradores/null". newPhotoUri quedaba != null → photoModel
            // NUNCA caía al respaldo de la foto del servidor → la piedra se
            // abría SIN foto justo al editar solo una vía sin tocar la imagen
            // (Álvaro, 2026-08-25, cazado con FileNotFoundException en el log).
            val photoFile = obj.textoONull("photoFile")
            // Y aunque el nombre sea válido, si el fichero no está (borrado,
            // copia fallida) NO se inventa un Uri roto: mejor sin foto nueva y
            // que se vea la del servidor.
            val newUri: Uri? = photoFile
                ?.let { File(carpeta(context), it) }
                ?.takeIf { it.exists() && it.length() > 0L }
                ?.toUri()
            val bloquesArr = obj.getJSONArray("bloques")
            val bloques = (0 until bloquesArr.length()).map { j ->
                val bo = bloquesArr.getJSONObject(j)
                val pathArr = bo.getJSONArray("linePath")
                val linePath = (0 until pathArr.length()).map { k ->
                    val pt = pathArr.getJSONArray(k)
                    Offset(pt.getDouble(0).toFloat(), pt.getDouble(1).toFloat())
                }
                BoulderBloqueForm(
                    id = bo.optString("id", UUID.randomUUID().toString()),
                    name = bo.optString("name", ""),
                    grade = bo.optString("grade", null).takeIf { bo.isNull("grade").not() },
                    startType = bo.optString("startType", null).takeIf { bo.isNull("startType").not() },
                    linePath = linePath,
                    facePhoto = bo.optString("facePhoto", null).takeIf { bo.isNull("facePhoto").not() },
                    existingLineId = bo.optString("existingLineId", null).takeIf { bo.isNull("existingLineId").not() },
                    description = bo.optString("description", null).takeIf { bo.isNull("description").not() },
                    variant = bo.optString("variant", null).takeIf { bo.isNull("variant").not() }
                )
            }
            EditFace(
                existingPhotoPath = obj.optString("existingPhotoPath", null).takeIf { obj.isNull("existingPhotoPath").not() },
                newPhotoUri = newUri,
                bloques = bloques
            )
        }
        Draft(blockId, faces, raiz.optLong("savedAt", 0))
    }.getOrNull()

    fun clear(context: Context, blockId: String) {
        runCatching { fichero(context, blockId).delete() }
        val prefijo = "${seguro(blockId)}-cara"
        carpeta(context).listFiles()?.forEach { f -> if (f.name.startsWith(prefijo)) f.delete() }
    }

    /**
     * ¿Hay algo local que merezca la pena guardar? Al EDITAR una piedra ya
     * existente siempre hay al menos una vía con nombre/grado (viene del
     * servidor) — no vale mirar solo si hay foto nueva, si no cancelar tras
     * editar SOLO el nombre o el grado de una vía no ofrecía guardar nada
     * (Rodrigo, 2026-08-22: "no sale nada de guardar editando"). Foto nueva
     * O cualquier vía con datos cuenta como contenido.
     */
    fun tieneContenido(faces: List<EditFace>): Boolean = faces.any { face ->
        face.newPhotoUri != null || face.bloques.any {
            it.name.isNotBlank() || it.grade != null || it.linePath.isNotEmpty()
        }
    }
}

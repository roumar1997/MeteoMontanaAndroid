package com.meteomontana.android.data.outbox

import androidx.compose.ui.geometry.Offset
import com.meteomontana.android.ui.screens.detail.BoulderBloqueForm
import com.meteomontana.android.data.photos.FotosLocales
import com.meteomontana.android.ui.screens.detail.BoulderFaceForm
import kotlinx.serialization.Serializable

/**
 * Propuesta de PIEDRA guardada sin conexión (OutboxType.CONTRIBUTION_BOULDER).
 * Las fotos ya están COPIADAS al almacenamiento privado de la app
 * ([localPhotoPath]): un content:// del picker caduca, un fichero propio no.
 * Al reconectar, OutboxFlusher sube cada foto, reconstruye las caras y envía
 * la contribución exactamente igual que el envío online.
 */
@Serializable
data class QueuedVia(
    val name: String,
    val grade: String?,
    val startType: String?,
    /** Puntos normalizados de la línea como [x, y] (Offset no es serializable). */
    val points: List<List<Float>>,
    val targetLineId: String?
)

@Serializable
data class QueuedFace(
    val localPhotoPath: String?,
    val vias: List<QueuedVia>
)

@Serializable
data class QueuedBoulder(
    val schoolId: String,
    val lat: Double,
    val lon: Double,
    val name: String?,
    val sectorBlockId: String?,
    val discipline: String,
    val geometry: String,
    val pathJson: String?,
    val direction: String,
    val faces: List<QueuedFace>
)

/**
 * Una cara al EDITAR una piedra existente. A diferencia de la de una piedra
 * nueva, aquí una cara puede tener ya foto en el servidor y no tocarse:
 *  - [localPhotoPath] != null → foto NUEVA nuestra, hay que subirla al reconectar.
 *  - si no, [existingPhotoPath] es la que ya tenía y se manda tal cual.
 */
@Serializable
data class QueuedEditFace(
    val localPhotoPath: String?,
    val existingPhotoPath: String?,
    val vias: List<QueuedVia>
)

/**
 * EDICIÓN de una piedra existente guardada sin red
 * (OutboxType.CONTRIBUTION_EDIT_BLOCK): añadir vías, poner o cambiar la foto de
 * una cara, retrazar un muro.
 *
 * Se manda el estado COMPLETO de las vías (las existentes con su
 * [QueuedVia.targetLineId]), igual que el envío online: el backend reconcilia
 * por ese id, así que los enganches del diario sobreviven.
 */
@Serializable
data class QueuedBlockEdit(
    val schoolId: String,
    val targetBlockId: String,
    val lat: Double,
    val lon: Double,
    val geometry: String,
    val pathJson: String?,
    val direction: String,
    val faces: List<QueuedEditFace>
)

/**
 * Ruta de un fichero PROPIO con la foto de esa cara, lista para encolar.
 *
 * Desde 2026-08-16 las fotos se copian ya AL ELEGIRLAS (ver
 * [com.meteomontana.android.data.photos.FotosLocales]), así que lo normal es que
 * [uri] apunte a una copia nuestra y aquí solo se compruebe. Se sigue pudiendo
 * copiar por los borradores guardados ANTES de ese cambio, que llevan dentro
 * direcciones del selector del sistema ya caducadas; en ese caso esto devuelve
 * null.
 *
 * @return la ruta, o null si no se pudo preparar. **Quien llama NO puede
 *   ignorar el null**: encolar la cara sin su foto pierde el trabajo del
 *   usuario en silencio, que es justo el bug que se arregló.
 */
suspend fun copyPhotoToOutbox(context: android.content.Context, uri: android.net.Uri): String? {
    // Ya es copia nuestra y sigue ahí → nada que copiar.
    if (uri.scheme == "file" && FotosLocales.existe(uri)) return uri.path
    return FotosLocales.copiar(context, uri).getOrNull()?.path
}

fun BoulderBloqueForm.toQueued() = QueuedVia(
    name = name, grade = grade, startType = startType,
    points = linePath.map { listOf(it.x, it.y) },
    targetLineId = existingLineId
)

/** Reconstruye las caras para reutilizar facesToBloquesJson en el flush.
 *  Devuelve las caras y, aparte, la ruta local de la foto de cada una (por id). */
fun QueuedBoulder.toFaces(): Pair<List<BoulderFaceForm>, Map<String, String?>> {
    val localByFace = HashMap<String, String?>()
    val forms = faces.map { f ->
        val form = BoulderFaceForm(
            photoUri = null,
            bloques = f.vias.map { v ->
                BoulderBloqueForm(
                    name = v.name, grade = v.grade, startType = v.startType,
                    linePath = v.points.map { Offset(it[0], it[1]) },
                    existingLineId = v.targetLineId
                )
            }.ifEmpty { listOf(BoulderBloqueForm()) }
        )
        localByFace[form.id] = f.localPhotoPath
        form
    }
    return forms to localByFace
}

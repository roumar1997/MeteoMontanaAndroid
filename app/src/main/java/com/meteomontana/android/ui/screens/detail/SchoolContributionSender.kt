package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.outbox.OutboxRepository
import com.meteomontana.android.data.outbox.OutboxType
import com.meteomontana.android.data.outbox.QueuedBoulder
import com.meteomontana.android.data.outbox.QueuedFace
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.port.NetworkMonitor
import com.meteomontana.android.domain.port.PhotoUploader
import com.meteomontana.android.domain.usecase.contributions.SubmitContributionUseCase
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Envío de contribuciones desde el detalle de escuela: las 8 variantes
 * (parking/sector, piedra con caras, asignar sector, añadir vías, correcciones,
 * editar vía) + su cola offline. Extraído de SchoolDetailViewModel (SRP): toda
 * la construcción de ContributionRequest vive aquí; el VM solo delega.
 */
class SchoolContributionSender @Inject constructor(
    private val submitContributionUseCase: SubmitContributionUseCase,
    private val photoUploader: PhotoUploader,
    private val fileReader: FileReader,
    private val outboxRepo: OutboxRepository,
    private val networkMonitor: NetworkMonitor
) {
    private val outboxJson = Json { ignoreUnknownKeys = true }

    /** Propuesta simple (parking/sector/corrección). Sin red → outbox y "éxito"
     *  para que la UI cierre; OutboxFlusher la enviará al reconectar. */
    suspend fun submit(schoolId: String, req: ContributionRequest): Result<Unit> {
        if (!networkMonitor.isOnline.value) {
            return runCatching { queueOffline(schoolId, req) }
        }
        return runCatching { submitContributionUseCase(schoolId, req); Unit }
    }

    /** Encola una contribución simple (parking/sector: sin fotos). */
    suspend fun queueOffline(schoolId: String, req: ContributionRequest) {
        outboxRepo.enqueue(
            OutboxType.CONTRIBUTION, schoolId,
            outboxJson.encodeToString(ContributionRequest.serializer(), req)
        )
    }

    /** Encola una propuesta de piedra (las fotos YA copiadas a rutas locales). */
    suspend fun queueBoulderOffline(
        schoolId: String,
        lat: Double, lon: Double, name: String?,
        sectorBlockId: String?, discipline: String, geometry: String,
        pathJson: String?, direction: String,
        faces: List<QueuedFace>
    ) {
        val q = QueuedBoulder(
            schoolId = schoolId, lat = lat, lon = lon, name = name,
            sectorBlockId = sectorBlockId, discipline = discipline,
            geometry = geometry, pathJson = pathJson, direction = direction,
            faces = faces
        )
        outboxRepo.enqueue(
            OutboxType.CONTRIBUTION_BOULDER, schoolId,
            outboxJson.encodeToString(QueuedBoulder.serializer(), q)
        )
    }

    suspend fun submitBoulder(
        schoolId: String,
        lat: Double, lon: Double,
        name: String?,
        bloques: List<BoulderBloqueForm>,
        photoRef: FileRef?,
        sectorBlockId: String? = null
    ): Result<Unit> = runCatching {
        val photoUrl = if (photoRef != null) {
            val bytes = fileReader.readBytes(photoRef)
            photoUploader.uploadBoulderPhoto(bytes, "image/jpeg", schoolId)
        } else null

        val req = ContributionRequest(
            type = "BOULDER",
            name = name?.takeIf { it.isNotBlank() },
            lat = lat,
            lon = lon,
            notes = null,
            description = null,
            proposedLat = null, proposedLon = null,
            correctionReason = null, targetBlockId = null, targetLineId = null,
            sectorBlockId = sectorBlockId,
            photoUrl = photoUrl,
            bloquesJson = bloques.toBloquesJson(),
            topoLinesJson = null
        )
        submitContributionUseCase(schoolId, req)
        Unit
    }

    /**
     * Propone una piedra con VARIAS CARAS (fotos). Sube la foto de cada cara y
     * construye un único `bloquesJson` donde cada vía lleva el `photoUrl` de su
     * cara; el backend las agrupa en caras. La portada = primera cara con foto.
     */
    suspend fun submitBoulderFaces(
        schoolId: String,
        lat: Double, lon: Double,
        name: String?,
        faces: List<BoulderFaceForm>,
        sectorBlockId: String? = null,
        discipline: String = "BOULDER",
        geometry: String = "POINT",
        path: String? = null,
        direction: String = "LTR"
    ): Result<Unit> = runCatching {
        val photoUrlByFace = HashMap<String, String?>()
        for (face in faces) {
            val uri = face.photoUri
            photoUrlByFace[face.id] = if (uri != null) {
                val bytes = fileReader.readBytes(FileRef(uri.toString()))
                photoUploader.uploadBoulderPhoto(bytes, "image/jpeg", schoolId)
            } else null
        }
        val coverPhoto = faces.firstNotNullOfOrNull { photoUrlByFace[it.id] }
        val req = ContributionRequest(
            type = "BOULDER",
            name = name?.takeIf { it.isNotBlank() },
            lat = lat, lon = lon,
            notes = null, description = null,
            proposedLat = null, proposedLon = null,
            correctionReason = null, targetBlockId = null, targetLineId = null,
            sectorBlockId = sectorBlockId,
            photoUrl = coverPhoto,
            bloquesJson = facesToBloquesJson(faces, photoUrlByFace),
            topoLinesJson = null,
            discipline = discipline,
            geometry = geometry,
            path = path,
            direction = direction
        )
        submitContributionUseCase(schoolId, req)
        Unit
    }

    /** Propone asignar un sector (ZONE) existente a una piedra (BLOCK) existente. */
    suspend fun submitAssignSector(
        schoolId: String,
        targetBlockId: String,
        targetLat: Double,
        targetLon: Double,
        sectorBlockId: String
    ): Result<Unit> = runCatching {
        val req = ContributionRequest(
            type = "ASSIGN_SECTOR",
            name = null,
            lat = targetLat,
            lon = targetLon,
            notes = null,
            description = null,
            proposedLat = null, proposedLon = null,
            correctionReason = null,
            targetBlockId = targetBlockId,
            targetLineId = null,
            sectorBlockId = sectorBlockId,
            photoUrl = null,
            bloquesJson = null,
            topoLinesJson = null
        )
        android.util.Log.d("AssignSector",
            "→ POST schoolId=$schoolId targetBlockId=$targetBlockId sectorBlockId=$sectorBlockId")
        try {
            submitContributionUseCase(schoolId, req)
            android.util.Log.d("AssignSector", "← OK")
        } catch (t: Throwable) {
            android.util.Log.e("AssignSector", "← FAIL", t)
            throw t
        }
        Unit
    }

    suspend fun submitAddLines(
        schoolId: String,
        targetBlockId: String,
        targetLat: Double,
        targetLon: Double,
        bloques: List<BoulderBloqueForm>
    ): Result<Unit> = runCatching {
        val req = ContributionRequest(
            type = "BOULDER",
            name = null,
            lat = targetLat,
            lon = targetLon,
            notes = null,
            description = null,
            proposedLat = null, proposedLon = null,
            correctionReason = null,
            targetBlockId = targetBlockId,
            targetLineId = null,
            photoUrl = null,
            bloquesJson = bloques.toBloquesJson(),
            topoLinesJson = null
        )
        submitContributionUseCase(schoolId, req)
        Unit
    }

    /** Sube una foto de piedra (cara nueva) y devuelve su URL. */
    suspend fun uploadBoulderPhoto(schoolId: String, ref: FileRef): Result<String> = runCatching {
        val bytes = fileReader.readBytes(ref)
        photoUploader.uploadBoulderPhoto(bytes, "image/jpeg", schoolId)
    }

    /**
     * Envía correcciones/añadidos de vías de una piedra en UNA propuesta. Cada vía
     * lleva su `existingLineId` (corrige) o no (añade) y su `facePhoto` (cara). El
     * backend distingue por nodo. Usado por el editor por cara (repintar + añadir +
     * cambiar foto).
     */
    suspend fun submitBoulderCorrections(
        schoolId: String,
        targetBlockId: String,
        targetLat: Double,
        targetLon: Double,
        bloques: List<BoulderBloqueForm>,
        geometry: String = "POINT",
        path: String? = null,
        direction: String = "LTR"
    ): Result<Unit> = runCatching {
        val req = ContributionRequest(
            type = "BOULDER", name = null, lat = targetLat, lon = targetLon,
            notes = null, description = null, proposedLat = null, proposedLon = null,
            correctionReason = null, targetBlockId = targetBlockId, targetLineId = null,
            photoUrl = null, bloquesJson = bloques.toBloquesJson(), topoLinesJson = null,
            geometry = geometry, path = path, direction = direction
        )
        submitContributionUseCase(schoolId, req)
        Unit
    }

    /** Corrección de una vía existente: actualiza nombre/grado/tipo/línea de targetLineId. */
    suspend fun submitEditLine(
        schoolId: String,
        targetBlockId: String,
        targetLineId: String,
        targetLat: Double,
        targetLon: Double,
        bloque: BoulderBloqueForm
    ): Result<Unit> = runCatching {
        val req = ContributionRequest(
            type = "BOULDER",
            name = null,
            lat = targetLat,
            lon = targetLon,
            notes = null,
            description = null,
            proposedLat = null, proposedLon = null,
            correctionReason = null,
            targetBlockId = targetBlockId,
            targetLineId = targetLineId,
            photoUrl = null,
            bloquesJson = listOf(bloque).toBloquesJson(),
            topoLinesJson = null
        )
        submitContributionUseCase(schoolId, req)
        Unit
    }
}

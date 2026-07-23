package com.meteomontana.android.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.outbox.toQueued
import kotlinx.coroutines.launch

// ORQUESTADOR del flujo "Proponer mejora" (reparto del antiguo fichero de
// 1.595 líneas). Las piezas visuales viven en:
//   ProposePickerDialogs.kt  → TypePickerDialog + SuccessDialog
//   PlaceFormDialog.kt       → ParkingFormDialog + SectorFormDialog (unificados)
//   BoulderFormDialog.kt     → formulario de piedra + BloqueRow
//   ProposeCommonUi.kt       → SubmitFooter, selectores, CumbreDialog

// ─── Estados internos del flujo ─────────────────────────────────────────────

/** Datos del marker fantasma que SchoolMap renderiza durante una corrección. */
data class CorrectionGhost(
    val originalId: String?,   // id del marker original a "fantasmear". null=escuela.
    val newLat: Double?,
    val newLon: Double?
)

private sealed interface ProposeStep {
    data object TypePicker    : ProposeStep
    data object WaitingMapTap : ProposeStep
    data class  Form(val lat: Double, val lon: Double)        : ProposeStep  // PARKING
    data class  BoulderForm(val lat: Double, val lon: Double) : ProposeStep  // BOULDER
    data class  WallTracing(val lat: Double, val lon: Double) : ProposeStep  // trazar muro en el mapa
    data class  SectorForm(val lat: Double, val lon: Double)  : ProposeStep  // SECTOR
    data object CorrectionPickTarget : ProposeStep                          // espera tap en marker existente
    data class  CorrectionMoving(
        val targetId: String?,    // null = mover escuela entera
        val targetName: String,
        val oldLat: Double,
        val oldLon: Double,
        val targetType: String,   // BLOCK / PARKING / ZONE / SCHOOL
        val newLat: Double? = null,  // null = aún no fijada
        val newLon: Double? = null
    ) : ProposeStep
    data class  Success(val queued: Boolean = false) : ProposeStep
}

// ─── Punto de entrada ────────────────────────────────────────────────────────

/**
 * Flujo completo de "Proponer mejora":
 *
 *   TypePicker → WaitingMapTap → Form(PARKING) | BoulderForm(BOULDER) → Success
 *
 * La pantalla padre (SchoolMap) debe:
 *   - Mostrar el banner "PULSA EN EL MAPA" cuando `waitingForTap == true`
 *   - Llamar a `onMapTap(lat, lon)` cuando el usuario toque el mapa en ese estado
 */
@Composable
fun ProposeContributionFlow(
    schoolName: String,
    schoolLat: Double = 0.0,
    schoolLon: Double = 0.0,
    waitingForTap: Boolean,
    onStartWaitingTap: () -> Unit,
    onMapTap: ((Double, Double) -> Unit) -> Unit,
    /** Registra callback al que el mapa llamará cuando el usuario toque un marker existente.
     *  El bloque contiene id, nombre, lat, lon, type. */
    onMarkerTapForCorrection: ((com.meteomontana.android.domain.model.Block) -> Unit) -> Unit = {},
    /** Cuando true → el mapa hace que los markers respondan al tap del usuario para corrección
     *  en vez del popup normal. SchoolMap debe reaccionar a este flag. */
    onCorrectionModeChange: (Boolean) -> Unit = {},
    /** Posición del marker fantasma (nueva posición candidata) + id del original a fantasmear.
     *  null = no hay corrección activa. */
    onGhostMarkerChange: (CorrectionGhost?) -> Unit = {},
    /** Nombre del target seleccionado para mostrar en el banner ("MOVIENDO: …"). null = ninguno. */
    onCorrectionTargetChange: (String?) -> Unit = {},
    /** Registra callback que SchoolMap llamará cuando el usuario pulse ACEPTAR. */
    onAcceptCorrection: ((() -> Unit) -> Unit) = {},
    /** Cuando true → el mapa entra en modo "traza el muro": cada tap añade un punto a
     *  la polilínea y se muestra el banner con DESHACER/LISTO. */
    onWallTracingChange: (Boolean) -> Unit = {},
    /** Empuja a SchoolMap la polilínea en construcción (puntos [lat,lon]) para previsualizarla. */
    onWallPreviewChange: (List<Pair<Double, Double>>) -> Unit = {},
    /** Registra callback que SchoolMap llamará al pulsar DESHACER (quita el último punto). */
    onWallUndo: ((() -> Unit) -> Unit) = {},
    /** Registra callback que SchoolMap llamará al pulsar LISTO (cierra el trazado). */
    onWallDone: ((() -> Unit) -> Unit) = {},
    /** Si true → el admin mueve directamente vía API admin sin pasar por flujo propuesta. */
    isAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onMyProposals: () -> Unit,
    viewModel: SchoolDetailViewModel
) {
    var step by remember { mutableStateOf<ProposeStep>(ProposeStep.TypePicker) }
    var boulderMode by remember { mutableStateOf(false) }

    // Estado del borrador BOULDER (elevado aquí para persistir entre BoulderForm y TopoDialog).
    // Una piedra puede tener VARIAS CARAS (fotos), cada una con sus vías.
    var boulderName by remember { mutableStateOf("") }
    var boulderFaces by remember { mutableStateOf(listOf(BoulderFaceForm())) }
    var boulderTopoFaceIdx by remember { mutableStateOf<Int?>(null) }  // cara cuyo editor de líneas está abierto
    var boulderSectorBlockId by remember { mutableStateOf<String?>(null) }
    var boulderDiscipline by remember { mutableStateOf("BOULDER") }  // BOULDER (bloque) / ROUTE (vía)
    // Geometría en el mapa: POINT (marcador) o LINE (muro = polilínea trazada en el mapa).
    var boulderGeometry by remember { mutableStateOf("POINT") }
    // Sentido de numeración de las vías del muro: LTR (izq→der) / RTL (der→izq).
    var boulderDirection by remember { mutableStateOf("LTR") }
    // Polilínea trazada del muro: lista de puntos [lat,lon] (vacía mientras geometry=POINT).
    var boulderPath by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }

    // Sectores (ZONE) existentes en la escuela — alimentan el dropdown del BoulderForm.
    val uiStateForSectors by viewModel.uiState.collectAsState()
    val sectorBlocks = (uiStateForSectors as? SchoolDetailUiState.Success)
        ?.blocks?.filter { it.type == "ZONE" } ?: emptyList()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Tipo de la última opción elegida en el picker — guía el WaitingMapTap a qué Form ir.
    var pickedType by remember { mutableStateOf("PARKING") }

    onMapTap { lat, lon ->
        when (val cur = step) {
            is ProposeStep.WaitingMapTap -> {
                step = when (pickedType) {
                    "BOULDER" -> ProposeStep.BoulderForm(lat, lon)
                    "SECTOR"  -> ProposeStep.SectorForm(lat, lon)
                    else      -> ProposeStep.Form(lat, lon)
                }
            }
            is ProposeStep.CorrectionMoving -> {
                // Cada tap reposiciona el fantasma. NO transiciona — solo ACEPTAR cierra.
                step = cur.copy(newLat = lat, newLon = lon)
                onGhostMarkerChange(CorrectionGhost(originalId = cur.targetId, newLat = lat, newLon = lon))
            }
            is ProposeStep.WallTracing -> {
                // Cada tap añade un punto a la polilínea del muro. LISTO cierra el modo.
                boulderPath = boulderPath + (lat to lon)
                onWallPreviewChange(boulderPath)
            }
            else -> {}
        }
    }

    // Botones DESHACER / LISTO del banner de trazado (SchoolMap los dispara).
    onWallUndo {
        boulderPath = boulderPath.dropLast(1)
        onWallPreviewChange(boulderPath)
    }
    onWallDone {
        val cur = step as? ProposeStep.WallTracing ?: return@onWallDone
        onWallTracingChange(false)
        step = ProposeStep.BoulderForm(cur.lat, cur.lon)
    }

    onMarkerTapForCorrection { tappedBlock ->
        if (step is ProposeStep.CorrectionPickTarget) {
            val isSchool = tappedBlock.id == "__SCHOOL__"
            val targetId = if (isSchool) null else tappedBlock.id
            val targetName = if (isSchool) "la escuela" else tappedBlock.name
            step = ProposeStep.CorrectionMoving(
                targetId = targetId,
                targetName = targetName,
                oldLat = tappedBlock.lat,
                oldLon = tappedBlock.lon,
                targetType = if (isSchool) "SCHOOL" else tappedBlock.type
            )
            onGhostMarkerChange(CorrectionGhost(originalId = targetId, newLat = null, newLon = null))
            onCorrectionTargetChange(targetName)
        }
    }

    // Registramos el callback de ACEPTAR — SchoolMap lo invoca al pulsar el botón.
    onAcceptCorrection {
        val cur = step as? ProposeStep.CorrectionMoving ?: return@onAcceptCorrection
        val newLat = cur.newLat ?: return@onAcceptCorrection
        val newLon = cur.newLon ?: return@onAcceptCorrection
        scope.launch {
            val result = if (isAdmin && cur.targetType == "SCHOOL") {
                runCatching { viewModel.adminMoveSchool(newLat, newLon) }
            } else if (isAdmin && cur.targetId != null) {
                runCatching { viewModel.adminMoveBlock(cur.targetId, newLat, newLon) }
            } else {
                viewModel.submitContribution(ContributionRequest(
                    type = "POSITION_CORRECTION",
                    name = cur.targetName,
                    lat = cur.oldLat, lon = cur.oldLon,
                    notes = null, description = null,
                    proposedLat = newLat, proposedLon = newLon,
                    correctionReason = null,
                    targetBlockId = cur.targetId,
                    photoUrl = null, bloquesJson = null, topoLinesJson = null
                ))
            }
            if (result.isSuccess) {
                onGhostMarkerChange(null)
                onCorrectionTargetChange(null)
                onCorrectionModeChange(false)
                step = ProposeStep.Success()
            }
        }
    }

    when (val s = step) {
        is ProposeStep.TypePicker -> TypePickerDialog(
            onParking = {
                boulderMode = false; pickedType = "PARKING"
                step = ProposeStep.WaitingMapTap
                onStartWaitingTap()
            },
            onBoulder = {
                boulderMode = true; pickedType = "BOULDER"
                boulderFaces = listOf(BoulderFaceForm())
                boulderName = ""
                boulderSectorBlockId = null
                boulderDiscipline = "BOULDER"
                boulderGeometry = "POINT"
                boulderDirection = "LTR"
                boulderPath = emptyList()
                step = ProposeStep.WaitingMapTap
                onStartWaitingTap()
            },
            onSector = {
                boulderMode = false; pickedType = "SECTOR"
                step = ProposeStep.WaitingMapTap
                onStartWaitingTap()
            },
            onCorrection = {
                step = ProposeStep.CorrectionPickTarget
                onCorrectionModeChange(true)
            },
            onDismiss = onDismiss
        )

        is ProposeStep.WaitingMapTap -> {
            // El banner "PULSA EN EL MAPA" lo pinta SchoolMap leyendo waitingForTap.
        }

        is ProposeStep.Form -> ParkingFormDialog(
            lat = s.lat,
            lon = s.lon,
            onCancel = onDismiss,
            onSubmit = { name, notes ->
                val req = ContributionRequest(
                    type = "PARKING",
                    name = name.takeIf { it.isNotBlank() },
                    lat = s.lat,
                    lon = s.lon,
                    notes = notes.takeIf { it.isNotBlank() },
                    description = null,
                    proposedLat = null, proposedLon = null,
                    correctionReason = null, targetBlockId = null,
                    photoUrl = null, bloquesJson = null, topoLinesJson = null
                )
                val result = viewModel.submitContribution(req)
                if (result.isSuccess) step = ProposeStep.Success()
                result.isSuccess
            },
            onSaveOffline = { name, notes ->
                scope.launch {
                    viewModel.queueContributionOffline(ContributionRequest(
                        type = "PARKING",
                        name = name.takeIf { it.isNotBlank() },
                        lat = s.lat, lon = s.lon,
                        notes = notes.takeIf { it.isNotBlank() },
                        description = null,
                        proposedLat = null, proposedLon = null,
                        correctionReason = null, targetBlockId = null,
                        photoUrl = null, bloquesJson = null, topoLinesJson = null
                    ))
                    step = ProposeStep.Success(queued = true)
                }
            }
        )

        is ProposeStep.BoulderForm -> {
            BoulderFormDialog(
                lat = s.lat,
                lon = s.lon,
                faces = boulderFaces,
                onFacesChange = { boulderFaces = it },
                onOpenTopo = { faceIdx -> boulderTopoFaceIdx = faceIdx },
                sectorBlocks = sectorBlocks,
                sectorBlockId = boulderSectorBlockId,
                onSectorChange = { boulderSectorBlockId = it },
                discipline = boulderDiscipline,
                onDisciplineChange = { boulderDiscipline = it },
                geometry = boulderGeometry,
                onGeometryChange = { boulderGeometry = it },
                direction = boulderDirection,
                onDirectionChange = { boulderDirection = it },
                path = boulderPath,
                onTraceWall = {
                    onWallPreviewChange(boulderPath)
                    onWallTracingChange(true)
                    step = ProposeStep.WallTracing(s.lat, s.lon)
                },
                onCancel = onDismiss,
                onSubmit = {
                    val result = viewModel.submitBoulderFacesContribution(
                        lat = s.lat, lon = s.lon,
                        name = boulderName,
                        faces = boulderFaces,
                        sectorBlockId = boulderSectorBlockId,
                        discipline = boulderDiscipline,
                        geometry = boulderGeometry,
                        path = if (boulderGeometry == "LINE" && boulderPath.isNotEmpty())
                            boulderPath.toPathJson() else null,
                        direction = boulderDirection
                    )
                    if (result.isSuccess) step = ProposeStep.Success()
                    result.isSuccess
                },
                onSaveOffline = {
                    scope.launch {
                        // Copia cada foto al almacenamiento de la app (un
                        // content:// del picker caduca; un fichero propio no).
                        val qFaces = boulderFaces.map { f ->
                            com.meteomontana.android.data.outbox.QueuedFace(
                                localPhotoPath = f.photoUri?.let {
                                    com.meteomontana.android.data.outbox.copyPhotoToOutbox(context, it)
                                },
                                vias = f.bloques.map { it.toQueued() }
                            )
                        }
                        viewModel.queueBoulderOffline(
                            lat = s.lat, lon = s.lon, name = boulderName,
                            sectorBlockId = boulderSectorBlockId,
                            discipline = boulderDiscipline,
                            geometry = boulderGeometry,
                            pathJson = if (boulderGeometry == "LINE" && boulderPath.isNotEmpty())
                                boulderPath.toPathJson() else null,
                            direction = boulderDirection,
                            faces = qFaces
                        )
                        step = ProposeStep.Success(queued = true)
                    }
                }
            )

            // Editor de líneas de UNA cara (se muestra encima del formulario).
            val topoIdx = boulderTopoFaceIdx
            val topoFace = topoIdx?.let { boulderFaces.getOrNull(it) }
            val topoPhoto = topoFace?.photoUri
            if (topoIdx != null && topoFace != null && topoPhoto != null) {
                ContributionTopoDialog(
                    photoUri = topoPhoto,
                    bloques = topoFace.bloques,
                    onSave = { updated ->
                        boulderFaces = boulderFaces.toMutableList().also {
                            it[topoIdx] = topoFace.copy(bloques = updated)
                        }
                        boulderTopoFaceIdx = null
                    },
                    onDismiss = { boulderTopoFaceIdx = null }
                )
            }
        }

        is ProposeStep.WallTracing -> {
            // Banner "TRAZA EL MURO" + DESHACER/LISTO + preview los pinta SchoolMap.
        }

        is ProposeStep.SectorForm -> SectorFormDialog(
            lat = s.lat, lon = s.lon,
            onCancel = onDismiss,
            onSubmit = { name, notes ->
                val req = ContributionRequest(
                    type = "SECTOR",
                    name = name.takeIf { it.isNotBlank() },
                    lat = s.lat, lon = s.lon,
                    notes = notes.takeIf { it.isNotBlank() },
                    description = null,
                    proposedLat = null, proposedLon = null,
                    correctionReason = null, targetBlockId = null,
                    photoUrl = null, bloquesJson = null, topoLinesJson = null
                )
                val result = viewModel.submitContribution(req)
                if (result.isSuccess) step = ProposeStep.Success()
                result.isSuccess
            },
            onSaveOffline = { name, notes ->
                scope.launch {
                    viewModel.queueContributionOffline(ContributionRequest(
                        type = "SECTOR",
                        name = name.takeIf { it.isNotBlank() },
                        lat = s.lat, lon = s.lon,
                        notes = notes.takeIf { it.isNotBlank() },
                        description = null,
                        proposedLat = null, proposedLon = null,
                        correctionReason = null, targetBlockId = null,
                        photoUrl = null, bloquesJson = null, topoLinesJson = null
                    ))
                    step = ProposeStep.Success(queued = true)
                }
            }
        )

        is ProposeStep.CorrectionPickTarget -> {
            // Banner lo pinta SchoolMap leyendo el flag correctionMode. Aquí sin dialog.
        }

        is ProposeStep.CorrectionMoving -> {
            // Banner + ghost marker + ACEPTAR los renderiza SchoolMap leyendo el state.
        }

        is ProposeStep.Success -> SuccessDialog(
            isAdmin = isAdmin,
            queued = s.queued,
            onClose = onDismiss,
            onMyProposals = { onDismiss(); onMyProposals() }
        )
    }
}

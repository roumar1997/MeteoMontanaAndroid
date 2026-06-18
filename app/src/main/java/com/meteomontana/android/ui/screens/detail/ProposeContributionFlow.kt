@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Moss
import com.meteomontana.android.ui.theme.OkDark
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import kotlinx.coroutines.launch

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
    data object Success       : ProposeStep
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

    // Sectores (ZONE) existentes en la escuela — alimentan el dropdown del BoulderForm.
    val uiStateForSectors by viewModel.uiState.collectAsState()
    val sectorBlocks = (uiStateForSectors as? SchoolDetailUiState.Success)
        ?.blocks?.filter { it.type == "ZONE" } ?: emptyList()

    val scope = rememberCoroutineScope()

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
            else -> {}
        }
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
                step = ProposeStep.Success
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
            BannerCancelOverlay(onCancel = onDismiss)
        }

        is ProposeStep.Form -> ParkingFormDialog(
            lat = s.lat,
            lon = s.lon,
            onCancel = onDismiss,
            onSubmit = { name, notes ->
                scope.launch {
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
                    if (result.isSuccess) step = ProposeStep.Success
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
                onCancel = onDismiss,
                onSubmit = {
                    scope.launch {
                        val result = viewModel.submitBoulderFacesContribution(
                            lat = s.lat, lon = s.lon,
                            name = boulderName,
                            faces = boulderFaces,
                            sectorBlockId = boulderSectorBlockId
                        )
                        if (result.isSuccess) step = ProposeStep.Success
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

        is ProposeStep.SectorForm -> SectorFormDialog(
            lat = s.lat, lon = s.lon,
            onCancel = onDismiss,
            onSubmit = { name, notes ->
                scope.launch {
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
                    if (result.isSuccess) step = ProposeStep.Success
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
            onClose = onDismiss,
            onMyProposals = { onDismiss(); onMyProposals() }
        )
    }
}

// ─── TypePickerDialog ────────────────────────────────────────────────────────

@Composable
private fun TypePickerDialog(
    onParking: () -> Unit,
    onBoulder: () -> Unit,
    onSector: () -> Unit,
    onCorrection: () -> Unit,
    onDismiss: () -> Unit
) {
    CumbreDialog(onDismiss = onDismiss) {
        Text(
            "Proponer una mejora",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "El admin revisará tu propuesta antes de publicarla.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.lg))

        TypeOption(
            icon = "◆",
            label = "AÑADIR PIEDRA",
            description = "Una roca con uno o más bloques/vías",
            enabled = true,
            onClick = onBoulder
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        TypeOption(
            icon = "+",
            label = "AÑADIR SECTOR",
            description = "Zona con varios bloques (ej: La Isla, Vertedero…)",
            enabled = true,
            onClick = onSector
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        TypeOption(
            icon = "■",
            label = "AÑADIR PARKING",
            description = "Punto de aparcamiento para acceder a la escuela",
            enabled = true,
            onClick = onParking
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        TypeOption(
            icon = "↔",
            label = "CORREGIR POSICIÓN",
            description = "Mover una piedra, parking o sector existente",
            enabled = true,
            onClick = onCorrection
        )

        Spacer(Modifier.height(Spacing.lg))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable(onClick = onDismiss)
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text("CERRAR", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TypeOption(
    icon: String,
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(icon, color = Terra.copy(alpha = alpha), style = MaterialTheme.typography.titleMedium)
        Column {
            Text(label, style = EyebrowTextStyle, color = Terra.copy(alpha = alpha))
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

// ─── Banner cancelar ──────────────────────────────────────────────────────────

@Composable
private fun BannerCancelOverlay(
    onCancel: () -> Unit,
    text: String = "ℹ PULSA EN EL MAPA DONDE QUIERES AÑADIR EL ELEMENTO"
) {
    // El banner visual lo pinta SchoolMap mediante el flag waitingMapTap / correctionMode.
    // Aquí solo dejamos pasar el cancel y el texto si quisieras render adicional.
}

// ─── SectorFormDialog ────────────────────────────────────────────────────────
@Composable
private fun SectorFormDialog(
    lat: Double,
    lon: Double,
    onCancel: () -> Unit,
    onSubmit: (name: String, notes: String) -> Unit
) {
    var name  by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    CumbreDialog(onDismiss = onCancel) {
        Text("Nuevo sector",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.xs))
        Text("Una zona con varios bloques. Se ubicará en el punto que pulsaste.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.lg))

        Text("NOMBRE", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ej: La Isla, Vertedero, Cuevas…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Spacer(Modifier.height(Spacing.md))

        Text("COORDENADAS (LAT, LON)", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text("%.6f, %.6f".format(lat, lon),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
            color = Terra)
        Spacer(Modifier.height(Spacing.md))

        Text("NOTAS (OPCIONAL)", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            placeholder = { Text("Tipo de roca, orientación, accesos…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Spacer(Modifier.height(Spacing.lg))

        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable(enabled = !sending, onClick = onCancel)
                .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center) {
                Text("CANCELAR", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Box(modifier = Modifier.weight(1.5f).clip(MaterialTheme.shapes.small)
                .background(Terra)
                .clickable(enabled = !sending && name.isNotBlank()) {
                    sending = true; onSubmit(name, notes)
                }
                .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center) {
                if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp),
                    color = Color.White, strokeWidth = 2.dp)
                else Text("ENVIAR PROPUESTA", style = EyebrowTextStyle, color = Color.White)
            }
        }
    }
}

// ─── ParkingFormDialog ────────────────────────────────────────────────────────

@Composable
private fun ParkingFormDialog(
    lat: Double,
    lon: Double,
    onCancel: () -> Unit,
    onSubmit: (name: String, notes: String) -> Unit
) {
    var name  by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    CumbreDialog(onDismiss = onCancel) {
        Text("Nuevo parking",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.xs))
        Text("Pulsa en el mapa para fijar la posición",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.lg))

        Text("NOMBRE (OPCIONAL)", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ej: Parking principal, Área forestal…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Spacer(Modifier.height(Spacing.md))

        Text("COORDENADAS (LAT, LON)", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text("%.6f, %.6f".format(lat, lon),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
            color = Terra)
        Text("✓ POSICIÓN DESDE EL MAPA", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(Spacing.md))

        Text("NOTAS (OPCIONAL)", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            placeholder = { Text("Capacidad, restricciones, horario…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Spacer(Modifier.height(Spacing.lg))

        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable(enabled = !sending, onClick = onCancel)
                .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center) {
                Text("CANCELAR", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Box(modifier = Modifier.weight(1.5f).clip(MaterialTheme.shapes.small)
                .background(Terra)
                .clickable(enabled = !sending) { sending = true; onSubmit(name, notes) }
                .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center) {
                if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp),
                    color = Color.White, strokeWidth = 2.dp)
                else Text("ENVIAR PROPUESTA", style = EyebrowTextStyle, color = Color.White)
            }
        }
    }
}

// ─── BoulderFormDialog ────────────────────────────────────────────────────────

@Composable
private fun BoulderFormDialog(
    lat: Double,
    lon: Double,
    faces: List<BoulderFaceForm>,
    onFacesChange: (List<BoulderFaceForm>) -> Unit,
    onOpenTopo: (Int) -> Unit,
    sectorBlocks: List<Block>,
    sectorBlockId: String?,
    onSectorChange: (String?) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    var sending by remember { mutableStateOf(false) }
    var sectorExpanded by remember { mutableStateOf(false) }
    var selectedFaceIdx by remember { mutableStateOf(0) }
    val faceIdx = selectedFaceIdx.coerceIn(0, (faces.size - 1).coerceAtLeast(0))
    val face = faces.getOrNull(faceIdx) ?: BoulderFaceForm()

    // Actualiza la cara seleccionada.
    fun updateFace(transform: (BoulderFaceForm) -> BoulderFaceForm) {
        onFacesChange(faces.toMutableList().also { it[faceIdx] = transform(it[faceIdx]) })
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) updateFace { it.copy(photoUri = uri) } }

    CumbreDialog(onDismiss = onCancel, scrollable = true) {
        Text("Nueva piedra",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.xs))
        Text("A esta piedra se le asignará un número automático al publicarse.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.md))

        // ── Sector (opcional) ────────────────────────────────────────────────────
        if (sectorBlocks.isNotEmpty()) {
            Text("SECTOR (OPCIONAL)", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            val selectedSectorName = sectorBlocks.firstOrNull { it.id == sectorBlockId }?.name
            ExposedDropdownMenuBox(
                expanded = sectorExpanded,
                onExpandedChange = { sectorExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedSectorName ?: "Sin sector",
                    onValueChange = {}, readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectorExpanded)
                    },
                    shape = MaterialTheme.shapes.small, colors = fieldColors()
                )
                ExposedDropdownMenu(
                    expanded = sectorExpanded,
                    onDismissRequest = { sectorExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sin sector",
                            style = MaterialTheme.typography.bodyMedium) },
                        onClick = { onSectorChange(null); sectorExpanded = false }
                    )
                    sectorBlocks.forEach { sect ->
                        DropdownMenuItem(
                            text = { Text(sect.name,
                                style = MaterialTheme.typography.bodyMedium) },
                            onClick = { onSectorChange(sect.id); sectorExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }

        // ── Coordenadas ──────────────────────────────────────────────────────────
        Text("COORDENADAS (LAT, LON)", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text("%.6f, %.6f".format(lat, lon),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono),
            color = Terra)
        Text("✓ POSICIÓN DESDE EL MAPA", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(Spacing.md))

        // ── Caras (fotos) ─────────────────────────────────────────────────────────
        // Una piedra grande no cabe en una foto: añade varias fotos, cada una con
        // sus vías. Pestañas para cambiar de foto; "+ AÑADIR FOTO" crea otra.
        Text("FOTOS DE LA PIEDRA", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            itemsIndexed(faces) { idx, _ ->
                val sel = idx == faceIdx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .then(if (sel) Modifier.background(Terra) else Modifier)
                        .border(1.dp, if (sel) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable { selectedFaceIdx = idx }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text("FOTO ${idx + 1}", style = EyebrowTextStyle,
                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable {
                            onFacesChange(faces + BoulderFaceForm())
                            selectedFaceIdx = faces.size  // selecciona la nueva
                        }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text("+ AÑADIR FOTO", style = EyebrowTextStyle, color = Terra)
                }
            }
        }
        if (faces.size > 1) {
            Spacer(Modifier.height(Spacing.xs))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable {
                        val newFaces = faces.toMutableList().also { it.removeAt(faceIdx) }
                        selectedFaceIdx = (faceIdx - 1).coerceAtLeast(0)
                        onFacesChange(newFaces)
                    }
                    .padding(vertical = Spacing.xs)
            ) {
                Text("✕ QUITAR ESTA FOTO", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(Spacing.md))

        // ── Foto de la cara seleccionada ───────────────────────────────────────────
        val photoUri = face.photoUri
        if (photoUri != null) {
            Box {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Foto ${faceIdx + 1} de la piedra",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xs)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable { updateFace { it.copy(photoUri = null) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable { photoLauncher.launch("image/*") }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text("CAMBIAR FOTO", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable { photoLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sin foto seleccionada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("SELECCIONAR FOTO", style = EyebrowTextStyle, color = Terra)
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // ── Vías de esta foto ──────────────────────────────────────────────────────
        Text("VÍAS EN ESTA FOTO", style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        face.bloques.forEachIndexed { idx, bloque ->
            BloqueRow(
                index = idx,
                bloque = bloque,
                onUpdate = { updated ->
                    updateFace { f -> f.copy(bloques = f.bloques.toMutableList().also { it[idx] = updated }) }
                },
                onDelete = if (face.bloques.size > 1) ({
                    updateFace { f -> f.copy(bloques = f.bloques.toMutableList().also { it.removeAt(idx) }) }
                }) else null
            )
            Spacer(Modifier.height(Spacing.xs))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable { updateFace { f -> f.copy(bloques = f.bloques + BoulderBloqueForm()) } }
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text("+ AÑADIR VÍA", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── Dibujar líneas de esta foto ────────────────────────────────────────────
        Spacer(Modifier.height(Spacing.sm))
        val hasLines = face.bloques.any { it.linePath.isNotEmpty() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (photoUri != null) Modifier.background(Terra)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                )
                .clickable(enabled = photoUri != null) { if (photoUri != null) onOpenTopo(faceIdx) }
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (hasLines) "✎ EDITAR LÍNEAS" else "✎ DIBUJAR LÍNEAS",
                style = EyebrowTextStyle,
                color = if (photoUri != null) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (photoUri == null) {
            Spacer(Modifier.height(2.dp))
            Text("Añade una foto para poder dibujar las líneas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(Spacing.lg))

        // ── Footer ────────────────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable(enabled = !sending, onClick = onCancel)
                .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center) {
                Text("CANCELAR", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Box(modifier = Modifier.weight(1.5f).clip(MaterialTheme.shapes.small)
                .background(Terra)
                .clickable(enabled = !sending) { sending = true; onSubmit() }
                .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center) {
                if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp),
                    color = Color.White, strokeWidth = 2.dp)
                else Text("ENVIAR PROPUESTA", style = EyebrowTextStyle, color = Color.White)
            }
        }
    }
}

// ─── BloqueRow ────────────────────────────────────────────────────────────────

@Composable
private fun BloqueRow(
    index: Int,
    bloque: BoulderBloqueForm,
    onUpdate: (BoulderBloqueForm) -> Unit,
    onDelete: (() -> Unit)?
) {
    var gradeExpanded by remember { mutableStateOf(false) }
    val gradeColor = colorForGrade(bloque.grade)
    val startTypes = listOf("PIE", "SIT", "LANCE", "TRAV")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // Número + nombre + eliminar
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // Número del bloque
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(gradeColor),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White)
            }
            // Nombre
            OutlinedTextField(
                value = bloque.name,
                onValueChange = { onUpdate(bloque.copy(name = it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nombre (opcional)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            // Eliminar bloque
            if (onDelete != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text("✕", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Grado + tipo de inicio
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // Dropdown de grado
            ExposedDropdownMenuBox(
                expanded = gradeExpanded,
                onExpandedChange = { gradeExpanded = it },
                modifier = Modifier.width(120.dp)
            ) {
                OutlinedTextField(
                    value = bloque.grade ?: "Grado",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeExpanded)
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = gradeColor,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                    )
                )
                ExposedDropdownMenu(
                    expanded = gradeExpanded,
                    onDismissRequest = { gradeExpanded = false }
                ) {
                    BOULDER_GRADES.forEach { grade ->
                        val gs = gradeStyle(grade)
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(gs.stroke))
                                    Text(grade, style = MaterialTheme.typography.bodyMedium)
                                }
                            },
                            onClick = {
                                onUpdate(bloque.copy(grade = grade))
                                gradeExpanded = false
                            }
                        )
                    }
                }
            }

            // Tipos de inicio (PIE/SIT/LANCE/TRAV)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(startTypes) { type ->
                    val sel = bloque.startType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (sel) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                            .clickable {
                                onUpdate(bloque.copy(startType = if (sel) null else type))
                            }
                            .padding(horizontal = Spacing.xs, vertical = 2.dp)
                    ) {
                        Text(
                            type,
                            style = EyebrowTextStyle,
                            color = if (sel) MaterialTheme.colorScheme.background
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Resumen de línea dibujada si existe
        if (bloque.linePath.isNotEmpty()) {
            Text(
                "✓ Línea dibujada (${bloque.linePath.size} puntos)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

// ─── SuccessDialog ────────────────────────────────────────────────────────────

@Composable
private fun SuccessDialog(
    isAdmin: Boolean = false,
    onClose: () -> Unit,
    onMyProposals: () -> Unit
) {
    CumbreDialog(onDismiss = onClose) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(Moss),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            }
            Spacer(Modifier.height(Spacing.lg))
            if (isAdmin) {
                Text("PUBLICADO", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text("Se ha publicado directamente en el mapa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("PROPUESTA ENVIADA", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text("Un admin la revisará en ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("24-48h.", style = MaterialTheme.typography.bodyMedium, color = Terra)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Te avisaremos por email y notificación\npush cuando haya respuesta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable(onClick = onClose).padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center) {
                    Text("CERRAR", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Box(modifier = Modifier.weight(1.5f).clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onBackground)
                    .clickable(onClick = onMyProposals).padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center) {
                    Text("VER MIS PROPUESTAS", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
}

// ─── Primitivas compartidas ───────────────────────────────────────────────────

@Composable
private fun CumbreDialog(
    onDismiss: () -> Unit,
    scrollable: Boolean = false,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(Spacing.lg)
        ) {
            val colMod = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
            Column(modifier = colMod) { content() }
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor   = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor   = Terra,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
)

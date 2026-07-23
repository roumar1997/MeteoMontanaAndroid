@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.meteomontana.android.domain.model.FileRef
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.components.TopoLine
import com.meteomontana.android.ui.components.TopoPhotoCanvas
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import kotlinx.coroutines.launch

/**
 * Una cara editable de la piedra dentro del editor: la foto actual (remota) y/o
 * una foto nueva elegida que la sustituye, más sus vías. Las vías existentes
 * llevan `existingLineId`; las nuevas no.
 */
internal data class EditFace(
    val existingPhotoPath: String?,   // foto remota actual (null si cara nueva)
    val newPhotoUri: Uri?,            // foto local elegida (sustituye a la anterior)
    val bloques: List<BoulderBloqueForm>
) {
    /** Modelo para Coil: la nueva si la hay, si no la URL remota. */
    val photoModel: Any? get() = newPhotoUri ?: existingPhotoPath?.takeIf { it.isNotBlank() }
    val hasPhoto: Boolean get() = photoModel != null
}

/** STAND/SIT/JUMP/TRAV (backend) → PIE/SIT/LANCE/TRAV (app). */
private fun startTypeForBoulderUi(raw: String?): String? = when (raw?.uppercase()) {
    "STAND", "PIE"  -> "PIE"
    "SIT"           -> "SIT"
    "SEMI"          -> "SEMI"
    "JUMP", "LANCE" -> "LANCE"
    "TRAV"          -> "TRAV"
    else            -> null
}

/**
 * Construye el estado editable inicial (caras + vías) desde una piedra existente.
 * Lo usa el contenedor (SchoolMap) para elevar el estado del editor y que
 * sobreviva mientras el diálogo se oculta para trazar el muro en el mapa.
 */
internal fun initialEditFaces(block: Block): List<EditFace> =
    block.facesOrDerived().let { fs ->
        if (fs.isEmpty()) listOf(EditFace(block.photoPath, null, listOf(BoulderBloqueForm())))
        else fs.map { f ->
            EditFace(
                existingPhotoPath = f.photoPath,
                newPhotoUri = null,
                bloques = f.lines.map { l ->
                    BoulderBloqueForm(
                        name = l.name, grade = l.grade,
                        startType = startTypeForBoulderUi(l.startType?.toString()),
                        linePath = com.meteomontana.android.ui.screens.topo.parseLineStroke(l.linePath).points,
                        existingLineId = l.id,
                        description = l.lineDescription,
                        variant = l.variant
                    )
                }.ifEmpty { listOf(BoulderBloqueForm()) }
            )
        }
    }

/**
 * Flujo "Editar / corregir vías" sobre una piedra existente — editor COMPLETO,
 * paridad con el de creación. Es un componente CONTROLADO: su estado (caras,
 * geometría, sentido, cara seleccionada, trazado) lo posee el contenedor para
 * que sobreviva al ocultarse mientras se traza el muro en el mapa.
 *
 *  - Corrige vías existentes y añade nuevas.
 *  - `+ AÑADIR FOTO`: nuevas caras a una piedra ya creada.
 *  - Reordena vías (▲▼) y mueve caras (◀▶) — define la numeración del muro.
 *  - Geometría PUNTO/MURO + sentido; trazado del muro en el mapa (`onTraceWall`).
 *
 * Al enviar manda el ESTADO COMPLETO (todas las vías en su orden + geometry/path/
 * direction). El backend (`reconcileWall`) reconcilia por `lineId` preservando
 * los enganches del diario, reaplica el orden y borra las omitidas.
 */
@Composable
internal fun AddLinesFlow(
    block: Block,
    viewModel: SchoolDetailViewModel,
    faces: List<EditFace>,
    onFacesChange: (List<EditFace>) -> Unit,
    selectedFace: Int,
    onSelectedFaceChange: (Int) -> Unit,
    geometry: String,
    onGeometryChange: (String) -> Unit,
    direction: String,
    onDirectionChange: (String) -> Unit,
    /** Polilínea ya re-trazada en el mapa (null = no se ha re-trazado → se
     *  conserva el trazado actual del bloque). */
    tracedPath: List<Pair<Double, Double>>?,
    onTraceWall: () -> Unit,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var showTopo by remember { mutableStateOf(false) }
    var showReorder by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val faceIdx = selectedFace.coerceIn(0, (faces.size - 1).coerceAtLeast(0))
    val face = faces.getOrElse(faceIdx) { EditFace(null, null, listOf(BoulderBloqueForm())) }
    val isWall = geometry == "LINE"

    fun updateFace(transform: (EditFace) -> EditFace) {
        onFacesChange(faces.toMutableList().also { it[faceIdx] = transform(it[faceIdx]) })
    }
    fun updateBloques(transform: (List<BoulderBloqueForm>) -> List<BoulderBloqueForm>) {
        updateFace { it.copy(bloques = transform(it.bloques)) }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) updateFace { it.copy(newPhotoUri = uri) } }

    // ModalBottomSheet como el resto de fichas (antes era un Dialog flotante y
    // la parte de abajo quedaba rara, con la pantalla asomando por detrás).
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.md)
        ) {
            Text(stringResource(R.string.add_lines_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.add_lines_description, block.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.lg))

            // ── Geometría (PUNTO / MURO) ────────────────────────────────────────
            EditWallSection(block, geometry, onGeometryChange, direction,
                onDirectionChange, tracedPath, onTraceWall)
            Spacer(Modifier.height(Spacing.lg))

            // ── Fotos (caras) ───────────────────────────────────────────────────
            Text(stringResource(R.string.add_lines_faces_title), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            EditFacesTabs(faces, faceIdx, onSelectedFaceChange, onFacesChange,
                onReorder = { showReorder = true })
            Spacer(Modifier.height(Spacing.md))

            // ── Foto de la cara seleccionada ────────────────────────────────────
            EditFacePhoto(face, faceIdx, onPickPhoto = {
                photoLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            })
            Spacer(Modifier.height(Spacing.md))

            // ── Vías de esta cara ───────────────────────────────────────────────
            Text(stringResource(R.string.add_lines_routes_of_photo), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isWall) {
                Spacer(Modifier.height(2.dp))
                val dirShort = if (direction == "LTR") stringResource(R.string.add_lines_direction_ltr_short)
                    else stringResource(R.string.add_lines_direction_rtl_short)
                Text(stringResource(R.string.add_lines_routes_numbering_note, dirShort),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.sm))

            val totalVias = faces.sumOf { it.bloques.size }
            val precedingCount = faces.take(faceIdx).sumOf { it.bloques.size }
            face.bloques.forEachIndexed { idx, b ->
                val globalPos = precedingCount + idx
                val number = when {
                    !isWall -> idx + 1
                    direction == "LTR" -> globalPos + 1
                    else -> totalVias - globalPos
                }
                Text(if (b.existingLineId != null) stringResource(R.string.add_lines_existing_route) else stringResource(R.string.add_lines_new_route),
                    style = EyebrowTextStyle,
                    color = if (b.existingLineId != null) MaterialTheme.colorScheme.onSurfaceVariant else Terra)
                AddLineRow(
                    displayNumber = number,
                    bloque = b,
                    onUpdate = { upd -> updateBloques { it.toMutableList().also { l -> l[idx] = upd } } },
                    // También las EXISTENTES se pueden borrar: el backend
                    // reconcilia (las omitidas por el editor se eliminan).
                    onDelete = {
                        updateBloques { it.toMutableList().also { l -> l.removeAt(idx) } }
                    },
                    onMoveUp = if (idx > 0) ({
                        updateBloques { it.toMutableList().also {
                            val t = it[idx - 1]; it[idx - 1] = it[idx]; it[idx] = t
                        } }
                    }) else null,
                    onMoveDown = if (idx < face.bloques.size - 1) ({
                        updateBloques { it.toMutableList().also {
                            val t = it[idx + 1]; it[idx + 1] = it[idx]; it[idx] = t
                        } }
                    }) else null
                )
                Spacer(Modifier.height(Spacing.xs))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable { updateBloques { it + BoulderBloqueForm() } }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.add_lines_add_new_route), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── Dibujar líneas ──────────────────────────────────────────────────
            if (face.hasPhoto) {
                Spacer(Modifier.height(Spacing.sm))
                val hasLines = face.bloques.any { it.linePath.isNotEmpty() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(Terra)
                        .clickable { showTopo = true }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (hasLines) stringResource(R.string.add_lines_edit_lines_on_photo) else stringResource(R.string.add_lines_draw_lines_on_photo),
                        style = EyebrowTextStyle, color = Color.White
                    )
                }
            } else {
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.add_lines_need_photo_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            error?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Spacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(enabled = !sending, onClick = onDismiss)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.common_cancel).uppercase(), style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                val submitErrorText = stringResource(R.string.add_lines_submit_error)
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .clip(MaterialTheme.shapes.small)
                        .background(Terra)
                        .clickable(enabled = !sending) {
                            sending = true
                            error = null
                            scope.launch {
                                // 1) Sube la foto nueva de las caras que se cambiaron.
                                val urlByFace = HashMap<Int, String>()
                                var uploadFailed = -1
                                faces.forEachIndexed { i, f ->
                                    val uri = f.newPhotoUri
                                    if (uri != null && uploadFailed < 0) {
                                        val url = viewModel.uploadBoulderPhoto(FileRef(uri.toString())).getOrNull()
                                        if (url == null) uploadFailed = i else urlByFace[i] = url
                                    }
                                }
                                // Si una foto no subió NO seguimos: antes esa cara quedaba
                                // sin foto y se colapsaba en la FOTO 1 con las demás.
                                if (uploadFailed >= 0) {
                                    error = "No se pudo subir la foto ${uploadFailed + 1}. Revisa la conexión y reinténtalo (si no, las caras se mezclarían en una sola)."
                                    sending = false
                                    return@launch
                                }
                                // 2) Estado COMPLETO: todas las vías en su orden, cada una
                                //    con la foto de su cara. Se omiten solo las filas NUEVAS
                                //    vacías. Las existentes van siempre (el backend reconcilia
                                //    por lineId y reaplica el orden).
                                val payload = faces.flatMapIndexed { i, f ->
                                    val fp = urlByFace[i] ?: f.existingPhotoPath
                                    f.bloques.mapNotNull { v ->
                                        val stamped = v.copy(facePhoto = fp)
                                        when {
                                            v.existingLineId != null -> stamped
                                            v.grade != null || v.name.isNotBlank() || v.linePath.isNotEmpty() -> stamped
                                            else -> null
                                        }
                                    }
                                }
                                if (payload.isEmpty()) { sending = false; onSuccess(); return@launch }
                                val pathJson = if (isWall) {
                                    val tp = tracedPath
                                    if (tp != null && tp.isNotEmpty()) tp.toPathJson() else block.path
                                } else null
                                val result = viewModel.submitBoulderCorrections(
                                    targetBlockId = block.id,
                                    targetLat = block.lat,
                                    targetLon = block.lon,
                                    bloques = payload,
                                    geometry = geometry,
                                    path = pathJson,
                                    direction = direction
                                )
                                if (result.isSuccess) onSuccess()
                                else {
                                    sending = false
                                    error = submitErrorText
                                }
                            }
                        }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp),
                        color = Color.White, strokeWidth = 2.dp)
                    else Text(stringResource(R.string.propose_submit), style = EyebrowTextStyle, color = Color.White)
                }
            }
        }
    }

    // Editor topo sobre la foto de la cara (la NUEVA si la cambiaste). Las vías
    // editables de esta cara YA se dibujan; no se pasan líneas de referencia
    // aparte (evita duplicar) ni se mezclan las de otras caras.
    val editorPhoto = face.newPhotoUri ?: face.existingPhotoPath?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    if (showTopo && editorPhoto != null) {
        ContributionTopoDialog(
            photoUri = editorPhoto,
            bloques = face.bloques,
            onSave = { updated ->
                updateBloques { updated }
                showTopo = false
            },
            onDismiss = { showTopo = false },
            existingLines = emptyList()
        )
    }

    if (showReorder) {
        ReorderFacesDialog(
            faces = faces,
            isWall = isWall,
            direction = direction,
            onMove = { from, to ->
                if (to in faces.indices && from in faces.indices) {
                    val list = faces.toMutableList()
                    val item = list.removeAt(from)
                    list.add(to, item)
                    onFacesChange(list)
                    // Mantén seleccionada la cara que el usuario está moviendo.
                    if (faceIdx == from) onSelectedFaceChange(to)
                }
            },
            onDismiss = { showReorder = false }
        )
    }
}

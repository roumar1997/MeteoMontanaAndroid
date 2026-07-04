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
                        description = l.lineDescription
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
            Text(stringResource(R.string.propose_geometry), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            GeometrySelector(selected = geometry, onSelect = onGeometryChange)
            if (isWall) {
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(R.string.propose_direction), style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                DirectionSelector(selected = direction, onSelect = onDirectionChange)

                // Trazado del muro en el mapa.
                Spacer(Modifier.height(Spacing.md))
                val hasTrace = tracedPath != null && tracedPath.isNotEmpty()
                val hasCurrent = !block.path.isNullOrBlank()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, Terra, MaterialTheme.shapes.small)
                        .clickable(onClick = onTraceWall)
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            hasTrace -> stringResource(R.string.add_lines_wall_traced, tracedPath!!.size)
                            hasCurrent -> stringResource(R.string.add_lines_wall_retrace)
                            else -> stringResource(R.string.add_lines_wall_trace)
                        },
                        style = EyebrowTextStyle, color = Terra
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    when {
                        hasTrace -> stringResource(R.string.add_lines_wall_new_trace_note)
                        hasCurrent -> stringResource(R.string.add_lines_wall_keep_trace_note)
                        else -> stringResource(R.string.add_lines_wall_no_trace_note)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            // ── Fotos (caras) ───────────────────────────────────────────────────
            Text(stringResource(R.string.add_lines_faces_title), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                itemsIndexed(faces) { i, _ ->
                    val on = i == faceIdx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .then(if (on) Modifier.background(Terra) else Modifier)
                            .border(1.dp, if (on) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                            .clickable { onSelectedFaceChange(i) }
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    ) {
                        Text(stringResource(R.string.add_lines_face_label, i + 1), style = EyebrowTextStyle,
                            color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                            .clickable {
                                onFacesChange(faces + EditFace(null, null, listOf(BoulderBloqueForm())))
                                onSelectedFaceChange(faces.size)  // selecciona la nueva
                            }
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    ) {
                        Text(stringResource(R.string.propose_add_photo), style = EyebrowTextStyle, color = Terra)
                    }
                }
            }
            if (faces.size > 1) {
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, Terra, MaterialTheme.shapes.small)
                            .clickable { showReorder = true }
                            .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.propose_reorder_photos), style = EyebrowTextStyle, color = Terra)
                    }
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.small)
                            .clickable {
                                onFacesChange(faces.toMutableList().also { it.removeAt(faceIdx) })
                                onSelectedFaceChange((faceIdx - 1).coerceAtLeast(0))
                            }
                            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.add_lines_remove_photo, faceIdx + 1), style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))

            // ── Foto de la cara seleccionada ────────────────────────────────────
            if (face.hasPhoto) {
                AsyncImage(
                    model = face.photoModel,
                    contentDescription = stringResource(R.string.add_lines_photo_alt, faceIdx + 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(Spacing.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable {
                            photoLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                        .padding(vertical = Spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (face.newPhotoUri != null) stringResource(R.string.add_lines_new_photo_pick_other)
                        else stringResource(R.string.add_lines_change_photo),
                        style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (face.newPhotoUri != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(stringResource(R.string.add_lines_new_photo_redraw_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable {
                            photoLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.add_lines_no_photo),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.add_lines_select_photo), style = EyebrowTextStyle, color = Terra)
                    }
                }
            }
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
                                faces.forEachIndexed { i, f ->
                                    val uri = f.newPhotoUri
                                    if (uri != null) {
                                        viewModel.uploadBoulderPhoto(FileRef(uri.toString()))
                                            .getOrNull()?.let { urlByFace[i] = it }
                                    }
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

/**
 * Panel para reordenar las fotos (caras) de la piedra VIÉNDOLAS todas: cada fila
 * es una miniatura con su posición y nº de vías, y sube/baja para colocarla. En
 * muro la posición define la numeración global de las vías.
 */
@Composable
private fun ReorderFacesDialog(
    faces: List<EditFace>,
    isWall: Boolean,
    direction: String,
    onMove: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var expandedFace by remember { mutableStateOf<Int?>(null) }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md)
        ) {
            Text(stringResource(R.string.add_lines_reorder_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (isWall) {
                    val dirShort = if (direction == "LTR") stringResource(R.string.add_lines_direction_ltr_short)
                        else stringResource(R.string.add_lines_direction_rtl_short)
                    stringResource(R.string.add_lines_reorder_wall_note, dirShort)
                } else stringResource(R.string.add_lines_reorder_simple_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))

            faces.forEachIndexed { i, f ->
                val isExpanded = expandedFace == i
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs)
                        .border(1.dp, if (isExpanded) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .padding(Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { expandedFace = if (isExpanded) null else i },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // Posición
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Terra),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                        // Miniatura
                        if (f.hasPhoto) {
                            AsyncImage(
                                model = f.photoModel,
                                contentDescription = stringResource(R.string.add_lines_face_label, i + 1),
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(2.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(2.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.add_lines_reorder_photo_routes, i + 1, f.bloques.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(if (isExpanded) stringResource(R.string.add_lines_reorder_hide_routes) else stringResource(R.string.add_lines_reorder_show_routes),
                                style = MaterialTheme.typography.bodySmall, color = Terra)
                        }
                        // Subir / bajar
                        Text("▲", style = MaterialTheme.typography.titleMedium,
                            color = if (i > 0) Terra else MaterialTheme.colorScheme.outline,
                            modifier = if (i > 0) Modifier.clickable { onMove(i, i - 1) } else Modifier)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("▼", style = MaterialTheme.typography.titleMedium,
                            color = if (i < faces.size - 1) Terra else MaterialTheme.colorScheme.outline,
                            modifier = if (i < faces.size - 1) Modifier.clickable { onMove(i, i + 1) } else Modifier)
                    }
                    // Vista expandida: foto con sus líneas dibujadas.
                    if (isExpanded) {
                        Spacer(Modifier.height(Spacing.sm))
                        val photoUrl = f.photoModel?.toString()
                        if (!photoUrl.isNullOrBlank()) {
                            val topoLines = f.bloques
                                .filter { it.linePath.isNotEmpty() }
                                .map { TopoLine(it.name, it.grade, it.startType, it.linePath) }
                            TopoPhotoCanvas(photoUrl = photoUrl, lines = topoLines)
                        } else {
                            Text(stringResource(R.string.add_lines_reorder_no_image),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(Terra)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text("✓ " + stringResource(R.string.propose_ready), style = EyebrowTextStyle, color = Color.White)
            }
        }
    }
}

/** Fila para una vía: número (posición) + nombre + grado + tipo + reordenar + eliminar. */
@Composable
internal fun AddLineRow(
    displayNumber: Int,
    bloque: BoulderBloqueForm,
    onUpdate: (BoulderBloqueForm) -> Unit,
    onDelete: (() -> Unit)?,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val gradeColor = colorForGrade(bloque.grade)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // Tirador de reordenar ▲▼
            if (onMoveUp != null || onMoveDown != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▲", style = MaterialTheme.typography.labelMedium,
                        color = if (onMoveUp != null) Terra else MaterialTheme.colorScheme.outline,
                        modifier = if (onMoveUp != null) Modifier.clickable(onClick = onMoveUp) else Modifier)
                    Text("▼", style = MaterialTheme.typography.labelMedium,
                        color = if (onMoveDown != null) Terra else MaterialTheme.colorScheme.outline,
                        modifier = if (onMoveDown != null) Modifier.clickable(onClick = onMoveDown) else Modifier)
                }
            }
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(gradeColor),
                contentAlignment = Alignment.Center
            ) {
                Text("$displayNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White)
            }
            OutlinedTextField(
                value = bloque.name,
                onValueChange = { onUpdate(bloque.copy(name = it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.add_lines_route_name_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
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

        // Grado: grid de chips de un toque (colores por dificultad).
        Text("Grado", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradeChipsGrid(selected = bloque.grade,
            onSelect = { onUpdate(bloque.copy(grade = it)) })

        // Tipo de inicio con nombre completo.
        Text("Tipo de inicio", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        StartTypeChips(selected = bloque.startType,
            onSelect = { onUpdate(bloque.copy(startType = it)) })

        // Descripción opcional (beta, salida, detalle a especificar).
        OutlinedTextField(
            value = bloque.description ?: "",
            onValueChange = { onUpdate(bloque.copy(description = it.takeIf { t -> t.isNotBlank() })) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Descripción (opcional)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            maxLines = 3,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        if (bloque.linePath.isNotEmpty()) {
            Text(stringResource(R.string.add_lines_route_drawn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary)
        }
    }
}

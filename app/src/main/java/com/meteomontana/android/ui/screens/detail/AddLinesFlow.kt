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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.Block
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
                        existingLineId = l.id
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
            Text("Editar piedra / muro",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Edita \"${block.name}\": corrige o añade vías, añade más fotos, " +
                "reordena y ajusta el muro. Un admin lo revisará (o se publica directo " +
                "si eres admin).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.lg))

            // ── Geometría (PUNTO / MURO) ────────────────────────────────────────
            Text("GEOMETRÍA", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            GeometrySelector(selected = geometry, onSelect = onGeometryChange)
            if (isWall) {
                Spacer(Modifier.height(Spacing.md))
                Text("SENTIDO DE NUMERACIÓN", style = EyebrowTextStyle,
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
                            hasTrace -> "✓ MURO TRAZADO (${tracedPath!!.size} PUNTOS) · RE-TRAZAR"
                            hasCurrent -> "✎ RE-TRAZAR EL MURO EN EL MAPA"
                            else -> "✎ TRAZAR EL MURO EN EL MAPA"
                        },
                        style = EyebrowTextStyle, color = Terra
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    when {
                        hasTrace -> "Se enviará el trazado nuevo."
                        hasCurrent -> "Se conserva el trazado actual si no lo re-trazas."
                        else -> "Este muro aún no tiene trazado: traza la base en el mapa."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            // ── Fotos (caras) ───────────────────────────────────────────────────
            Text("FOTOS DE LA PIEDRA", style = EyebrowTextStyle,
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
                        Text("FOTO ${i + 1}", style = EyebrowTextStyle,
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
                        Text("+ AÑADIR FOTO", style = EyebrowTextStyle, color = Terra)
                    }
                }
            }
            if (faces.size > 1) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    if (isWall) "ORDEN DE LAS FOTOS · la numeración del muro las recorre en este orden"
                    else "ORDEN DE LAS FOTOS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    val canLeft = faceIdx > 0
                    val canRight = faceIdx < faces.size - 1
                    Text("◀ MOVER", style = EyebrowTextStyle,
                        color = if (canLeft) Terra else MaterialTheme.colorScheme.outline,
                        modifier = if (canLeft) Modifier.clickable {
                            onFacesChange(faces.toMutableList().also {
                                val t = it[faceIdx - 1]; it[faceIdx - 1] = it[faceIdx]; it[faceIdx] = t
                            })
                            onSelectedFaceChange(faceIdx - 1)
                        } else Modifier)
                    Text("MOVER ▶", style = EyebrowTextStyle,
                        color = if (canRight) Terra else MaterialTheme.colorScheme.outline,
                        modifier = if (canRight) Modifier.clickable {
                            onFacesChange(faces.toMutableList().also {
                                val t = it[faceIdx + 1]; it[faceIdx + 1] = it[faceIdx]; it[faceIdx] = t
                            })
                            onSelectedFaceChange(faceIdx + 1)
                        } else Modifier)
                    Spacer(Modifier.weight(1f))
                    Text("✕ QUITAR ESTA FOTO", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable {
                            onFacesChange(faces.toMutableList().also { it.removeAt(faceIdx) })
                            onSelectedFaceChange((faceIdx - 1).coerceAtLeast(0))
                        })
                }
            }
            Spacer(Modifier.height(Spacing.md))

            // ── Foto de la cara seleccionada ────────────────────────────────────
            if (face.hasPhoto) {
                AsyncImage(
                    model = face.photoModel,
                    contentDescription = "Foto ${faceIdx + 1} de la piedra",
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
                        if (face.newPhotoUri != null) "✓ FOTO NUEVA · ELEGIR OTRA" else "CAMBIAR FOTO DE ESTA CARA",
                        style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (face.newPhotoUri != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Foto nueva: redibuja las vías de esta cara sobre ella antes de enviar.",
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
                        Text("Sin foto en esta cara",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("SELECCIONAR FOTO", style = EyebrowTextStyle, color = Terra)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))

            // ── Vías de esta cara ───────────────────────────────────────────────
            Text("VÍAS DE ESTA FOTO", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isWall) {
                Spacer(Modifier.height(2.dp))
                Text("Nº = posición en el muro (${if (direction == "LTR") "izq→der" else "der→izq"}). Reordena con ▲▼.",
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
                Text(if (b.existingLineId != null) "VÍA EXISTENTE" else "NUEVA",
                    style = EyebrowTextStyle,
                    color = if (b.existingLineId != null) MaterialTheme.colorScheme.onSurfaceVariant else Terra)
                AddLineRow(
                    displayNumber = number,
                    bloque = b,
                    onUpdate = { upd -> updateBloques { it.toMutableList().also { l -> l[idx] = upd } } },
                    onDelete = if (b.existingLineId == null) ({
                        updateBloques { it.toMutableList().also { l -> l.removeAt(idx) } }
                    }) else null,
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
                Text("+ AÑADIR VÍA NUEVA", style = EyebrowTextStyle,
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
                        if (hasLines) "✎ EDITAR LÍNEAS SOBRE LA FOTO" else "✎ DIBUJAR LÍNEAS SOBRE LA FOTO",
                        style = EyebrowTextStyle, color = Color.White
                    )
                }
            } else {
                Spacer(Modifier.height(Spacing.xs))
                Text("Añade una foto a esta cara para poder dibujar las líneas.",
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
                    Text("CANCELAR", style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurface)
                }
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
                                    error = "No se pudo enviar la propuesta. Inténtalo de nuevo."
                                }
                            }
                        }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp),
                        color = Color.White, strokeWidth = 2.dp)
                    else Text("ENVIAR PROPUESTA", style = EyebrowTextStyle, color = Color.White)
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
                placeholder = { Text("Nombre (opcional)",
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

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                        Text(type, style = EyebrowTextStyle,
                            color = if (sel) MaterialTheme.colorScheme.background
                                    else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        if (bloque.linePath.isNotEmpty()) {
            Text("✓ Línea dibujada (${bloque.linePath.size} puntos)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary)
        }
    }
}

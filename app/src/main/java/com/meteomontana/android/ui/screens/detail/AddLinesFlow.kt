@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.ui.components.toTopoLines
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import kotlinx.coroutines.launch

/**
 * Flujo "+ AÑADIR VÍAS" sobre una piedra existente.
 *
 * Reutiliza foto del block y solo permite añadir bloques (vías) nuevos con su
 * grado, tipo de inicio, y opcionalmente dibujar la línea sobre la foto. Al
 * enviar genera una contribución BOULDER con `targetBlockId = block.id` que
 * el admin aprueba: el backend añade las líneas al bloque existente.
 */
@Composable
fun AddLinesFlow(
    block: Block,
    viewModel: SchoolDetailViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var showTopo by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Editor POR CARA: una piedra puede tener varias fotos. Por cada cara se
    // precargan SUS vías existentes (para repintarlas/corregirlas) y se pueden
    // añadir nuevas; cada cara se edita sobre SU foto. `faceBloques[i]` = vías
    // editables de la cara i (existentes con existingLineId + nuevas).
    val faces = remember(block) { block.facesOrDerived() }
    var faceBloques by remember {
        mutableStateOf(
            if (faces.isEmpty()) listOf(listOf(BoulderBloqueForm()))
            else faces.map { f ->
                f.lines.map { l ->
                    BoulderBloqueForm(
                        name = l.name, grade = l.grade,
                        startType = startTypeForBoulderUi(l.startType?.toString()),
                        linePath = com.meteomontana.android.ui.screens.topo.parseLineStroke(l.linePath).points,
                        existingLineId = l.id
                    )
                }
            }
        )
    }
    var selectedFace by remember { mutableStateOf(0) }
    val faceIdx = selectedFace.coerceIn(0, (faceBloques.size - 1).coerceAtLeast(0))
    val facePhoto = faces.getOrNull(faceIdx)?.photoPath ?: block.photoPath
    val faceLines = faces.getOrNull(faceIdx)?.lines ?: block.lines
    val bloques = faceBloques.getOrElse(faceIdx) { listOf(BoulderBloqueForm()) }
    fun updateFace(transform: (List<BoulderBloqueForm>) -> List<BoulderBloqueForm>) {
        faceBloques = faceBloques.toMutableList().also { it[faceIdx] = transform(it[faceIdx]) }
    }

    // Foto NUEVA por cara (1b): al cambiarla, toda esa cara se moverá a la imagen
    // nueva al enviar; conviene redibujar sus vías sobre ella.
    var newPhotoByFace by remember { mutableStateOf<Map<Int, Uri>>(emptyMap()) }
    val newPhotoUri = newPhotoByFace[faceIdx]
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) newPhotoByFace = newPhotoByFace + (faceIdx to uri) }

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
            Text("Editar / corregir vías",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Edita las vías de \"${block.name}\" por foto: corrige nombre/grado/" +
                "tipo, redibújalas, cambia la foto de la cara o añade nuevas. " +
                "Un admin lo revisará (o se publica directo si eres admin).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.lg))

            // Selector de cara (solo si la piedra tiene varias fotos).
            if (faces.size > 1) {
                Text("¿EN QUÉ FOTO?", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(faces.size) { i ->
                        val on = i == faceIdx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (on) Terra else MaterialTheme.colorScheme.surface)
                                .border(1.dp, if (on) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                                .clickable { selectedFace = i }
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        ) {
                            Text("FOTO ${i + 1}", style = EyebrowTextStyle,
                                color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }

            // Vías de ESTA cara (existentes para repintar/corregir + nuevas)
            Text("VÍAS DE ESTA FOTO", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))

            bloques.forEachIndexed { idx, b ->
                Text(if (b.existingLineId != null) "VÍA EXISTENTE" else "NUEVA",
                    style = EyebrowTextStyle,
                    color = if (b.existingLineId != null) MaterialTheme.colorScheme.onSurfaceVariant else Terra)
                AddLineRow(
                    index = idx,
                    bloque = b,
                    onUpdate = { upd -> updateFace { it.toMutableList().also { l -> l[idx] = upd } } },
                    // Solo las nuevas se pueden quitar (el backend no borra existentes).
                    onDelete = if (b.existingLineId == null) ({
                        updateFace { it.toMutableList().also { l -> l.removeAt(idx) } }
                    }) else null
                )
                Spacer(Modifier.height(Spacing.xs))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable { updateFace { it + BoulderBloqueForm() } }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text("+ AÑADIR VÍA NUEVA", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Cambiar la foto de esta cara (mejorarla) y redibujar (1b).
            Spacer(Modifier.height(Spacing.sm))
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
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (newPhotoUri == null) "CAMBIAR FOTO DE ESTA CARA" else "✓ FOTO NUEVA · ELEGIR OTRA",
                    style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (newPhotoUri != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text("Foto nueva: redibuja las vías de esta cara sobre ella antes de enviar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Dibujar líneas (si la cara tiene foto existente o has elegido una nueva)
            if (!facePhoto.isNullOrBlank() || newPhotoUri != null) {
                Spacer(Modifier.height(Spacing.sm))
                val hasLines = bloques.any { it.linePath.isNotEmpty() }
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
                Text("Esta piedra no tiene foto, no puedes dibujar líneas.",
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
                                for ((i, uri) in newPhotoByFace) {
                                    viewModel.uploadBoulderPhoto(FileRef(uri.toString()))
                                        .getOrNull()?.let { urlByFace[i] = it }
                                }
                                // 2) Aplana todas las caras: cada vía con su facePhoto
                                //    (la nueva si la cara cambió de foto). Vías existentes
                                //    solo si su cara cambió de foto o si las modificaste;
                                //    nuevas siempre que tengan algo.
                                val payload = faceBloques.flatMapIndexed { i, vias ->
                                    val movedUrl = urlByFace[i]
                                    val fp = movedUrl ?: (faces.getOrNull(i)?.photoPath ?: block.photoPath)
                                    vias.mapNotNull { v ->
                                        val stamped = v.copy(facePhoto = fp)
                                        if (v.existingLineId != null) {
                                            val orig = block.lines.firstOrNull { it.id == v.existingLineId }
                                            val changed = movedUrl != null || orig == null || boulderLineChanged(v, orig)
                                            if (changed) stamped else null
                                        } else if (v.grade != null || v.name.isNotBlank() || v.linePath.isNotEmpty()) {
                                            stamped
                                        } else null
                                    }
                                }
                                if (payload.isEmpty()) { sending = false; onSuccess(); return@launch }
                                val result = viewModel.submitBoulderCorrections(
                                    targetBlockId = block.id,
                                    targetLat = block.lat,
                                    targetLon = block.lon,
                                    bloques = payload
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
    val editorPhoto = newPhotoUri ?: facePhoto?.let { Uri.parse(it) }
    if (showTopo && editorPhoto != null) {
        ContributionTopoDialog(
            photoUri = editorPhoto,
            bloques = bloques,
            onSave = { updated ->
                updateFace { updated }
                showTopo = false
            },
            onDismiss = { showTopo = false },
            existingLines = emptyList()
        )
    }
}

/** STAND/SIT/JUMP/TRAV (backend) → PIE/SIT/LANCE/TRAV (app). */
private fun startTypeForBoulderUi(raw: String?): String? = when (raw?.uppercase()) {
    "STAND", "PIE"  -> "PIE"
    "SIT"           -> "SIT"
    "JUMP", "LANCE" -> "LANCE"
    "TRAV"          -> "TRAV"
    else            -> null
}

/** ¿La vía editable difiere de la original del catálogo? */
private fun boulderLineChanged(b: BoulderBloqueForm, orig: com.meteomontana.android.domain.model.BlockLine): Boolean {
    if (b.name != orig.name) return true
    if ((b.grade ?: "") != (orig.grade ?: "")) return true
    if ((b.startType ?: "") != (startTypeForBoulderUi(orig.startType?.toString()) ?: "")) return true
    val origPts = com.meteomontana.android.ui.screens.topo.parseLineStroke(orig.linePath).points
    if (b.linePath.size != origPts.size) return true
    for (i in b.linePath.indices) {
        if (kotlin.math.abs(b.linePath[i].x - origPts[i].x) > 0.001f ||
            kotlin.math.abs(b.linePath[i].y - origPts[i].y) > 0.001f) return true
    }
    return false
}

/** Fila para un bloque/vía nueva: nombre + grado + tipo de inicio + eliminar. */
@Composable
internal fun AddLineRow(
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
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(gradeColor),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}",
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

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import android.net.Uri
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
    var bloques by remember { mutableStateOf(listOf(BoulderBloqueForm())) }
    var showTopo by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Caras (fotos) de la piedra: las vías nuevas se añaden a la cara elegida y se
    // dibujan SOBRE su foto (no se mezclan con las de otras caras).
    val faces = remember(block) { block.facesOrDerived() }
    var selectedFace by remember { mutableStateOf(0) }
    val faceIdx = selectedFace.coerceIn(0, (faces.size - 1).coerceAtLeast(0))
    val facePhoto = faces.getOrNull(faceIdx)?.photoPath ?: block.photoPath
    val faceLines = faces.getOrNull(faceIdx)?.lines ?: block.lines

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
            Text("Añadir vías a la piedra",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Estás añadiendo bloques nuevos a \"${block.name}\". " +
                "Las vías existentes no se tocan.",
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

            // Lista de bloques nuevos
            Text("NUEVAS VÍAS", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))

            bloques.forEachIndexed { idx, b ->
                AddLineRow(
                    index = idx,
                    bloque = b,
                    onUpdate = { upd ->
                        bloques = bloques.toMutableList().also { it[idx] = upd }
                    },
                    onDelete = if (bloques.size > 1) ({
                        bloques = bloques.toMutableList().also { it.removeAt(idx) }
                    }) else null
                )
                Spacer(Modifier.height(Spacing.xs))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable { bloques = bloques + BoulderBloqueForm() }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text("+ AÑADIR BLOQUE", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Dibujar líneas (solo si la cara elegida tiene foto)
            if (!facePhoto.isNullOrBlank()) {
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
                                val result = viewModel.submitAddLinesContribution(
                                    targetBlockId = block.id,
                                    targetLat = block.lat,
                                    targetLon = block.lon,
                                    bloques = bloques.filter {
                                        it.grade != null || it.name.isNotBlank() || it.linePath.isNotEmpty()
                                    }.map { it.copy(facePhoto = facePhoto) }   // van a la cara elegida
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

    // Editor topo precargado con la foto de la CARA elegida y solo sus vías como
    // referencia (no mezcla las de otras caras).
    if (showTopo && !facePhoto.isNullOrBlank()) {
        ContributionTopoDialog(
            photoUri = Uri.parse(facePhoto),
            bloques = bloques,
            onSave = { updated ->
                bloques = updated
                showTopo = false
            },
            onDismiss = { showTopo = false },
            existingLines = faceLines.toTopoLines()
        )
    }
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

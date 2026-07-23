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

// Fila de via del editor + reordenar caras. Reparto del antiguo AddLinesFlow.kt de 848 lineas.

/**
 * Panel para reordenar las fotos (caras) de la piedra VIÉNDOLAS todas: cada fila
 * es una miniatura con su posición y nº de vías, y sube/baja para colocarla. En
 * muro la posición define la numeración global de las vías.
 */
@Composable
internal fun ReorderFacesDialog(
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

        // Variante opcional: distingue vías homónimas ("directa", "extensión").
        OutlinedTextField(
            value = bloque.variant ?: "",
            onValueChange = { if (it.length <= 60) onUpdate(bloque.copy(variant = it.takeIf { t -> t.isNotBlank() })) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.line_variant_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Text(
            stringResource(R.string.line_variant_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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

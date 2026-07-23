package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra

// Secciones del editor "Editar / corregir vías" (AddLinesFlow). Antes vivían
// inline en un único @Composable de ~470 líneas; con nombre propio, cada
// sección se lee y se cambia sola (reparto de la 3ª ronda, auditoría 2026-07-19).

/** Geometría PUNTO/MURO + sentido + botón de trazado del muro en el mapa. */
@Composable
internal fun EditWallSection(
    block: Block,
    geometry: String,
    onGeometryChange: (String) -> Unit,
    direction: String,
    onDirectionChange: (String) -> Unit,
    tracedPath: List<Pair<Double, Double>>?,
    onTraceWall: () -> Unit
) {
    Text(stringResource(R.string.propose_geometry), style = EyebrowTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.xs))
    GeometrySelector(selected = geometry, onSelect = onGeometryChange)
    if (geometry == "LINE") {
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
}

/** Pestañas de caras (FOTO 1, FOTO 2, + AÑADIR) + reordenar/quitar. */
@Composable
internal fun EditFacesTabs(
    faces: List<EditFace>,
    faceIdx: Int,
    onSelectedFaceChange: (Int) -> Unit,
    onFacesChange: (List<EditFace>) -> Unit,
    onReorder: () -> Unit
) {
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
                    .clickable(onClick = onReorder)
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
}

/** Foto de la cara seleccionada + cambiar/seleccionar. */
@Composable
internal fun EditFacePhoto(
    face: EditFace,
    faceIdx: Int,
    onPickPhoto: () -> Unit
) {
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
                .clickable(onClick = onPickPhoto)
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
                .clickable(onClick = onPickPhoto),
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
}

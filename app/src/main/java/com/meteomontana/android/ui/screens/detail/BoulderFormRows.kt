package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.util.CatalogLabels
import com.meteomontana.android.util.AppText
import com.meteomontana.android.ui.theme.CumbrePillShape
import com.meteomontana.android.ui.theme.terraFillColor
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeChipColor
import kotlinx.coroutines.launch

// Filas del formulario de piedra: la ficha de cada vía y el selector de orientación.

@Composable
internal fun BloqueRow(
    index: Int,
    bloque: BoulderBloqueForm,
    onUpdate: (BoulderBloqueForm) -> Unit,
    onDelete: (() -> Unit)?,
    // Número mostrado en el círculo. En muro = nº global en vivo (orden + dirección).
    displayNumber: Int = index + 1,
    // Tirador para reordenar la vía (null = extremo, deshabilitado).
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val gradeColor = gradeChipColor(bloque.grade)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // Tirador ▲▼ + número + nombre + eliminar
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // Tirador para reordenar (subir/bajar) — visible solo si hay dónde mover.
            if (onMoveUp != null || onMoveDown != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▲", style = MaterialTheme.typography.labelMedium,
                        color = if (onMoveUp != null) Terra
                                else MaterialTheme.colorScheme.outline,
                        modifier = if (onMoveUp != null) Modifier.clickable(onClick = onMoveUp) else Modifier)
                    Text("▼", style = MaterialTheme.typography.labelMedium,
                        color = if (onMoveDown != null) Terra
                                else MaterialTheme.colorScheme.outline,
                        modifier = if (onMoveDown != null) Modifier.clickable(onClick = onMoveDown) else Modifier)
                }
            }
            // Número del bloque (en muro = nº global en vivo)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(gradeColor),
                contentAlignment = Alignment.Center
            ) {
                Text("$displayNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White)
            }
            // Nombre
            OutlinedTextField(
                value = bloque.name,
                onValueChange = { onUpdate(bloque.copy(name = it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.boulder_form_dialog_v2_nombre_opcional),
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

        // Grado: grid de chips de un toque (colores por dificultad).
        Text(stringResource(R.string.boulder_form_dialog_v2_grado), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradeChipsGrid(selected = bloque.grade,
            onSelect = { onUpdate(bloque.copy(grade = it)) })

        // Tipo de inicio con nombre completo.
        Text(stringResource(R.string.boulder_form_dialog_v2_tipo_de_inicio), style = MaterialTheme.typography.labelMedium,
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
            shape = MaterialTheme.shapes.small
        )
        Text(
            stringResource(R.string.line_variant_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Descripción opcional (beta, salida, detalle) — paridad con el editor
        // de vías (AddLineRow). Faltaba SOLO al CREAR piedra; ahora coinciden.
        OutlinedTextField(
            value = bloque.description ?: "",
            onValueChange = { onUpdate(bloque.copy(description = it.takeIf { t -> t.isNotBlank() })) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.boulder_form_dialog_descripcion_opcional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            maxLines = 3,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        // Resumen de línea dibujada si existe
        if (bloque.linePath.isNotEmpty()) {
            Text(
                stringResource(R.string.boulder_form_dialog_v2_linea_dibujada_1_s_puntos, bloque.linePath.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}


/** F: fila de chips de orientación (mismo lenguaje discontinuo-terra votable). */
@Composable
internal fun OrientationPickRow(label: String, selected: String?, onPick: (String) -> Unit) {
    Column {
        Text(label, style = EyebrowTextStyle.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        // Brujula tambien AQUI, creando la piedra: es cuando estas delante de
        // ella y sabes hacia donde mira. Informa; el chip lo eliges tu.
        val rumbo = com.meteomontana.android.ui.components.rememberDeviceHeading(stepDegrees = 2f)
        if (rumbo != null) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)) {
                com.meteomontana.android.ui.components.CompassDial(
                    rumbo, modifier = Modifier.size(64.dp))
                Column {
                    Text(
                        stringResource(R.string.boulder_form_dialog_v2_estas_mirando_al,
                            CatalogLabels.aspect(com.meteomontana.android.domain.util.Aspect.fromDegrees(rumbo)),
                            com.meteomontana.android.domain.util.Aspect.degreesLabel(rumbo)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.boulder_form_dialog_v2_si_estas_mirando_la_pared),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(BOULDER_ASPECTS.size) { i ->
                val a = BOULDER_ASPECTS[i]
                val active = selected == a
                Box(Modifier
                    .background(
                        if (active) com.meteomontana.android.ui.theme.Terra
                        else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(6.dp))
                    .border(1.dp,
                        com.meteomontana.android.ui.theme.Terra.copy(alpha = if (active) 1f else 0.55f),
                        RoundedCornerShape(6.dp))
                    .clickable { onPick(a) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(a, style = EyebrowTextStyle.copy(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)),
                        color = if (active) androidx.compose.ui.graphics.Color.White
                                else com.meteomontana.android.ui.theme.Terra)
                }
            }
        }
    }
}

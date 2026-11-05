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

/**
 * «Muro, sector y numeración» del formulario de piedra, plegado: geometría PUNTO/MURO, trazar el muro, sentido de
 * numeración y sector. Abierto de entrada solo si ya es relevante (muro trazado o sector elegido). Sacado de
 * BoulderFormDialog (2026-11-05).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun BoulderAdvancedOptions(
    geometry: String, onGeometryChange: (String) -> Unit,
    direction: String, onDirectionChange: (String) -> Unit,
    path: List<Pair<Double, Double>>, onTraceWall: () -> Unit,
    sectorBlocks: List<Block>, sectorBlockId: String?, onSectorChange: (String?) -> Unit
) {
    var advancedOpen by remember { mutableStateOf(geometry == "LINE" || sectorBlockId != null) }
    var sectorExpanded by remember { mutableStateOf(false) }
    // ── Opciones avanzadas: muro, sector y numeración (plegado) ─────────────
    // El 90% de las propuestas son una piedra normal: geometría de muro,
    // sentido de numeración y sector viven plegados para no estorbar. Se
    // abre solo si ya son relevantes (muro trazado o sector elegido).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (advancedOpen) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable { advancedOpen = !advancedOpen }
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.boulder_form_dialog_muro_sector_y_numeracion),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(if (geometry == "LINE") stringResource(R.string.boulder_form_dialog_v3_muro_de_puntos, path.size) +
                        (sectorBlockId?.let { stringResource(R.string.boulder_form_dialog_v3_con_sector_suffix) } ?: "")
                     else if (sectorBlockId != null) stringResource(R.string.boulder_form_dialog_v3_con_sector_asignado)
                     else stringResource(R.string.boulder_form_dialog_v3_solo_si_pared_larga),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (advancedOpen) "▲" else "▼",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (advancedOpen) {
        Spacer(Modifier.height(Spacing.sm))
        // ── Geometría: PUNTO o MURO ───────────────────────────────────────────────
        Text(stringResource(R.string.propose_geometry), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Text(stringResource(R.string.boulder_form_dialog_v2_punto_una_piedra_suelta_muro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        GeometrySelector(selected = geometry, onSelect = onGeometryChange)
        if (geometry == "LINE") {
            Spacer(Modifier.height(Spacing.xs))
            val traced = path.size >= 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CumbrePillShape)
                    .then(
                        if (traced) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CumbrePillShape)
                        else Modifier.background(terraFillColor())
                    )
                    .clickable(onClick = onTraceWall)
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        traced -> stringResource(R.string.boulder_form_dialog_v4_muro_de_puntos_re_trazar, path.size)
                        path.size == 1 -> stringResource(R.string.boulder_form_dialog_v4_trazar_el_muro_1_punto)
                        else -> stringResource(R.string.boulder_form_dialog_v4_trazar_el_muro_en_el)
                    },
                    style = EyebrowTextStyle,
                    color = if (traced) MaterialTheme.colorScheme.onSurface else Color.White
                )
            }
            if (!traced) {
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.boulder_form_dialog_v2_toca_al_menos_2_puntos),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.propose_direction), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            DirectionSelector(selected = direction, onSelect = onDirectionChange)
        }
        Spacer(Modifier.height(Spacing.md))
        // ── Sector (opcional) ────────────────────────────────────────────────────
        if (sectorBlocks.isNotEmpty()) {
            Text(stringResource(R.string.boulder_form_dialog_v2_sector_opcional), style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            val selectedSectorName = sectorBlocks.firstOrNull { it.id == sectorBlockId }?.name
            ExposedDropdownMenuBox(
                expanded = sectorExpanded,
                onExpandedChange = { sectorExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedSectorName ?: stringResource(R.string.boulder_form_dialog_v4_sin_sector),
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
                        text = { Text(stringResource(R.string.boulder_form_dialog_v2_sin_sector),
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
    }
}

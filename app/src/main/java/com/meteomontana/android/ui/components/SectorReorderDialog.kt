package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/**
 * Numerar piedras por sector: elegir sector (o "sin sector"), ver sus
 * piedras en el orden actual y subir/bajar para corregirlo cuando una se
 * añadió fuera de orden respecto al camino real, o pedir un primer orden
 * automático por distancia al parking más cercano (Álvaro, 2026-09-14 —
 * mismo comportamiento que SchoolSectorReorderSheet en iOS, sin arrastrar:
 * botones subir/bajar en vez de drag, para no meter una librería nueva
 * solo para esto).
 */
@Composable
fun SectorReorderDialog(
    schoolId: String,
    blocks: List<Block>,
    busy: Boolean,
    onReorder: (sectorBlockId: String?, orderedBlockIds: List<String>, onDone: (Boolean) -> Unit) -> Unit,
    onAutoReorder: (sectorBlockId: String?, onDone: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val sectors = remember(blocks) { blocks.filter { it.type == "ZONE" } }
    var selectedSectorId by remember { mutableStateOf<String?>(null) } // null = "sin sector"
    var ordered by remember { mutableStateOf<List<Block>>(emptyList()) }
    var dirty by remember { mutableStateOf(false) }
    var localBusy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun recompute() {
        ordered = blocks
            .filter { it.type == "BLOCK" && it.sectorBlockId == selectedSectorId }
            .sortedBy { it.name.toIntOrNull() ?: Int.MAX_VALUE }
        dirty = false
        errorMsg = null
    }

    LaunchedEffect(selectedSectorId, blocks) { recompute() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.sector_reorder_dialog_v2_ordenar_piedras), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.sector_reorder_dialog_v2_cerrar), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                SectorChip("SIN SECTOR", selectedSectorId == null) { selectedSectorId = null }
                sectors.forEach { z ->
                    SectorChip(z.name.ifBlank { "SECTOR" }.uppercase(), selectedSectorId == z.id) {
                        selectedSectorId = z.id
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (ordered.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(Spacing.lg), Alignment.Center) {
                    Text(
                        stringResource(R.string.sector_reorder_dialog_v2_este_sector_todavia_no_tiene),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(ordered, key = { it.id }) { block ->
                        val idx = ordered.indexOf(block)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${idx + 1}", style = MaterialTheme.typography.titleMedium,
                                color = Terra, modifier = Modifier.padding(end = Spacing.sm)
                            )
                            Text(
                                block.name.ifBlank { "(sin número)" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (idx > 0) {
                                    ordered = ordered.toMutableList().apply {
                                        val tmp = this[idx - 1]; this[idx - 1] = this[idx]; this[idx] = tmp
                                    }
                                    dirty = true
                                }
                            }, enabled = idx > 0) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.sector_reorder_dialog_v2_subir))
                            }
                            IconButton(onClick = {
                                if (idx < ordered.size - 1) {
                                    ordered = ordered.toMutableList().apply {
                                        val tmp = this[idx + 1]; this[idx + 1] = this[idx]; this[idx] = tmp
                                    }
                                    dirty = true
                                }
                            }, enabled = idx < ordered.size - 1) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.sector_reorder_dialog_v2_bajar))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
            }

            Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
                OutlinedButton(
                    onClick = {
                        localBusy = true; errorMsg = null
                        onAutoReorder(selectedSectorId) { ok ->
                            localBusy = false
                            if (ok) recompute() else errorMsg = "No se pudo calcular el orden."
                        }
                    },
                    enabled = !busy && !localBusy && ordered.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (localBusy) CircularProgressIndicator(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.sector_reorder_dialog_v2_auto_ordenar_por_gps))
                }
                Spacer(Modifier.height(Spacing.xs))
                Button(
                    onClick = {
                        localBusy = true; errorMsg = null
                        onReorder(selectedSectorId, ordered.map { it.id }) { ok ->
                            localBusy = false
                            if (ok) recompute() else errorMsg = "No se pudo guardar."
                        }
                    },
                    enabled = !busy && !localBusy && dirty,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sector_reorder_dialog_v2_guardar_orden))
                }
            }
        }
    }
}

@Composable
private fun SectorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(vertical = Spacing.sm)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Terra else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(
            label, style = EyebrowTextStyle,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

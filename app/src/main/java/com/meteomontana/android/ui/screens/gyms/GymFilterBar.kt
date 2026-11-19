package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.GymFilters
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.theme.Spacing

private val DISTANCE_STEPS = listOf(10.0, 25.0, 50.0, 100.0)

/**
 * Selector «Roca · Rocódromos · Muros gratis» de Escuelas. Las cápsulas pasan a otra línea si no caben
 * (pantalla estrecha o letra grande), en vez de cortar «MUROS GRATIS».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolsLayerPicker(layer: SchoolsLayer, onChange: (SchoolsLayer) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        CumbreChip(stringResource(R.string.gym_layer_rock).uppercase(), layer == SchoolsLayer.ROCK, { onChange(SchoolsLayer.ROCK) })
        CumbreChip(stringResource(R.string.gym_layer_gyms).uppercase(), layer == SchoolsLayer.ROCODROMOS,
            { onChange(SchoolsLayer.ROCODROMOS) })
        CumbreChip(stringResource(R.string.gym_layer_free).uppercase(), layer == SchoolsLayer.FREE_WALLS,
            { onChange(SchoolsLayer.FREE_WALLS) })
    }
}

/** Filtros en cápsulas: Gratis, Bloque, Cuerda, Abierto ahora, Favoritos, cadena y distancia (se desliza si no caben). */
@Composable
fun GymFilterBar(
    filters: GymFilters,
    chains: List<String>,
    showFreeFilter: Boolean,
    onFree: () -> Unit,
    onBoulder: () -> Unit,
    onRope: () -> Unit,
    onOpenNow: () -> Unit,
    onFavorites: () -> Unit,
    onChain: (String?) -> Unit,
    onDistance: (Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (showFreeFilter) CumbreChip(stringResource(R.string.gym_filter_free), filters.onlyFree, onFree)
        CumbreChip(stringResource(R.string.gym_filter_boulder), filters.boulder, onBoulder)
        CumbreChip(stringResource(R.string.gym_filter_rope), filters.rope, onRope)
        CumbreChip(stringResource(R.string.gym_filter_open_now), filters.openNow, onOpenNow)
        CumbreChip(stringResource(R.string.gym_filter_favorites), filters.onlyFavorites, onFavorites)
        if (chains.isNotEmpty()) {
            MenuChip(
                label = filters.chain ?: stringResource(R.string.gym_filter_chain),
                selected = filters.chain != null,
                options = listOf<String?>(null) + chains,
                optionLabel = { it ?: stringResource(R.string.gym_filter_chain_all) },
                onPick = onChain
            )
        }
        MenuChip(
            label = filters.maxDistanceKm?.let { stringResource(R.string.gym_filter_distance, it.toInt()) }
                ?: stringResource(R.string.gym_filter_distance_any),
            selected = filters.maxDistanceKm != null,
            options = listOf<Double?>(null) + DISTANCE_STEPS,
            optionLabel = { km -> km?.let { stringResource(R.string.gym_filter_distance,
                it.toInt()) } ?: stringResource(R.string.gym_filter_distance_any_option) },
            onPick = onDistance
        )
    }
}

@Composable
private fun <T> MenuChip(
    label: String,
    selected: Boolean,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onPick: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        CumbreChip(label, selected, { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { open = false; onPick(option) })
            }
        }
    }
}

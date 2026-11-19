package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.GymFilters
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.screens.schools.ChipRow
import com.meteomontana.android.ui.screens.schools.DISTANCE_OPTIONS
import com.meteomontana.android.ui.screens.schools.Section
import com.meteomontana.android.ui.screens.schools.TabButton
import com.meteomontana.android.util.AppText

/** Radio por defecto, el mismo que Escuelas (`SchoolFilters.maxDistanceKm`). */
internal const val DEFAULT_GYM_DISTANCE_KM = 50.0

/** Cuántos filtros se han movido respecto a lo de base (el número de «FILTROS (n)»). */
internal fun gymActiveFilterCount(f: GymFilters): Int = listOf(
    f.maxDistanceKm != DEFAULT_GYM_DISTANCE_KM, f.boulder, f.rope, f.openNow, f.onlyFree, f.onlyStreet,
    f.showClosed, f.chain != null, f.onlyFavorites
).count { it }

/** Dos segmentos de la fila entera, con el mismo ancho (como «Escuelas | Vías/Bloques»), el primero o el segundo elegido. */
@Composable
internal fun SegmentedPair(
    first: String,
    second: String,
    firstSelected: Boolean,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(3.dp)
    ) {
        TabButton(first, firstSelected, Modifier.weight(1f), onFirst)
        TabButton(second, !firstSelected, Modifier.weight(1f), onSecond)
    }
}

/** Selector «Roca | Rocódromos» de Escuelas: dos segmentos que ocupan toda la fila. */
@Composable
fun SchoolsLayerPicker(layer: SchoolsLayer, onChange: (SchoolsLayer) -> Unit, modifier: Modifier = Modifier) {
    SegmentedPair(
        first = stringResource(R.string.gym_layer_rock).uppercase(),
        second = stringResource(R.string.gym_layer_gyms).uppercase(),
        firstSelected = layer == SchoolsLayer.ROCK,
        onFirst = { onChange(SchoolsLayer.ROCK) },
        onSecond = { onChange(SchoolsLayer.ROCODROMOS) },
        modifier = modifier.padding(vertical = 4.dp)
    )
}

/**
 * Panel de filtros que se despliega bajo «FILTROS (n)», con las mismas secciones y chips que el de Escuelas:
 * distancia (mismos valores), modalidad, mostrar (gratis, de calle, abierto ahora, cerrados) y cadena.
 * El orden es por cercanía (si hay ubicación) y no se elige.
 */
@Composable
internal fun GymFiltersPanel(
    filters: GymFilters,
    chains: List<String>,
    onDistance: (Double?) -> Unit,
    onBoulder: () -> Unit,
    onRope: () -> Unit,
    onFree: () -> Unit,
    onStreet: () -> Unit,
    onOpenNow: () -> Unit,
    onClosed: () -> Unit,
    onChain: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section(stringResource(R.string.w_distance_caps)) {
            ChipRow(
                items = DISTANCE_OPTIONS,
                isSelected = { it == filters.maxDistanceKm },
                label = { km -> km?.let { "${it.toInt()} km" } ?: AppText.get(R.string.w_all) },
                onClick = onDistance
            )
        }
        Section(stringResource(R.string.gym_section_discipline).uppercase()) {
            Chips {
                CumbreChip(stringResource(R.string.gym_filter_boulder), filters.boulder, onBoulder)
                CumbreChip(stringResource(R.string.gym_filter_rope), filters.rope, onRope)
            }
        }
        Section(stringResource(R.string.gym_filter_show).uppercase()) {
            Chips {
                CumbreChip(stringResource(R.string.gym_filter_free), filters.onlyFree, onFree)
                CumbreChip(stringResource(R.string.gym_filter_street), filters.onlyStreet, onStreet)
                CumbreChip(stringResource(R.string.gym_filter_open_now), filters.openNow, onOpenNow)
                CumbreChip(stringResource(R.string.gym_filter_closed), filters.showClosed, onClosed)
            }
        }
        if (chains.isNotEmpty()) {
            Section(stringResource(R.string.gym_filter_chain).uppercase()) {
                Chips {
                    CumbreChip(stringResource(R.string.gym_filter_chain_all), filters.chain == null, onClick = { onChain(null) })
                    chains.forEach { c -> CumbreChip(c, filters.chain.equals(c, ignoreCase = true), onClick = { onChain(c) }) }
                }
            }
        }
    }
}

/** Fila de chips que se desliza si no caben (nunca se cortan ni ensanchan la pantalla). */
@Composable
private fun Chips(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content) }
    }
}

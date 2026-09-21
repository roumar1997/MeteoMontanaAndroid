package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.util.CatalogLabels
import com.meteomontana.android.util.AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/**
 * Barra de filtros estilo PWA: 5 secciones apiladas (distancia, estilo, roca,
 * favoritos, ordenar). Cada sección es una fila horizontal scrollable de chips.
 */
@Composable
fun SchoolFiltersBar(
    filters: SchoolFilters,
    onDistance: (Double?) -> Unit,
    onStyle: (StyleFilter) -> Unit,
    onRockToggle: (String) -> Unit,
    onOnlyFavorites: (Boolean) -> Unit,
    onSort: (SortBy) -> Unit,
    onOnlySavedOffline: (Boolean) -> Unit = {},
    onClearRocks: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Section(stringResource(R.string.w_distance_caps)) {
            ChipRow(
                items = DISTANCE_OPTIONS,
                isSelected = { it == filters.maxDistanceKm },
                label = { if (it == null) AppText.get(R.string.w_all) else "${it.toInt()} km" },
                onClick = onDistance
            )
        }
        Section(stringResource(R.string.w_style_caps)) {
            ChipRow(
                items = StyleFilter.entries,
                isSelected = { it == filters.style },
                label = { AppText.get(it.labelRes) },
                onClick = onStyle
            )
        }
        // TIPO DE ROCA con chip "Todas" al principio (limpia la selección), como iOS.
        Section(stringResource(R.string.school_filters_bar_v3_tipo_de_roca)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CumbreChip(
                        label = stringResource(R.string.school_filters_bar_v2_todas),
                        selected = filters.rockTypes.isEmpty(),
                        onClick = onClearRocks
                    )
                }
                items(ROCK_TYPES) { rock ->
                    CumbreChip(
                        label = CatalogLabels.rock(rock),
                        selected = rock in filters.rockTypes,
                        onClick = { onRockToggle(rock) }
                    )
                }
            }
        }
        // MOSTRAR: una sola fila tri-estado (Todas/Favoritos/Guardados), como iOS.
        Section(stringResource(R.string.w_show_caps_tristate)) {
            val current = when {
                filters.onlyFavorites -> ShowMode.Favorites
                filters.onlySavedOffline -> ShowMode.Saved
                else -> ShowMode.All
            }
            ChipRow(
                items = ShowMode.entries,
                isSelected = { it == current },
                label = { AppText.get(it.labelRes) },
                onClick = { sel ->
                    when (sel) {
                        ShowMode.Favorites -> {
                            if (filters.onlySavedOffline) onOnlySavedOffline(false)
                            onOnlyFavorites(true)
                        }
                        ShowMode.Saved -> {
                            if (filters.onlyFavorites) onOnlyFavorites(false)
                            onOnlySavedOffline(true)
                        }
                        else -> {
                            if (filters.onlyFavorites) onOnlyFavorites(false)
                            if (filters.onlySavedOffline) onOnlySavedOffline(false)
                        }
                    }
                }
            )
        }
        Section(stringResource(R.string.school_filters_bar_v3_ordenar_por)) {
            ChipRow(
                items = SortBy.entries,
                isSelected = { it == filters.sortBy },
                label = { AppText.get(it.labelRes) },
                onClick = onSort
            )
        }
    }
}

/** Los tres estados de la fila MOSTRAR (excluyentes entre sí). */
private enum class ShowMode(@androidx.annotation.StringRes val labelRes: Int) {
    All(R.string.w_all), Favorites(R.string.w_favourites), Saved(R.string.w_saved)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        content()
    }
}

@Composable
private fun <T> ChipRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onClick: (T) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            CumbreChip(
                label = label(item),
                selected = isSelected(item),
                onClick = { onClick(item) }
            )
        }
    }
}

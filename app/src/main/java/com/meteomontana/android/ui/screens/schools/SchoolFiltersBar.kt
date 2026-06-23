package com.meteomontana.android.ui.screens.schools

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
        Section("DISTANCIA") {
            ChipRow(
                items = DISTANCE_OPTIONS,
                isSelected = { it == filters.maxDistanceKm },
                label = { if (it == null) "Todas" else "${it.toInt()} km" },
                onClick = onDistance
            )
        }
        Section("ESTILO") {
            ChipRow(
                items = StyleFilter.entries,
                isSelected = { it == filters.style },
                label = { it.label },
                onClick = onStyle
            )
        }
        // TIPO DE ROCA con chip "Todas" al principio (limpia la selección), como iOS.
        Section("TIPO DE ROCA") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CumbreChip(
                        label = "Todas",
                        selected = filters.rockTypes.isEmpty(),
                        onClick = onClearRocks
                    )
                }
                items(ROCK_TYPES) { rock ->
                    CumbreChip(
                        label = rock,
                        selected = rock in filters.rockTypes,
                        onClick = { onRockToggle(rock) }
                    )
                }
            }
        }
        // MOSTRAR: una sola fila tri-estado (Todas/Favoritos/Guardados), como iOS.
        Section("MOSTRAR") {
            val current = when {
                filters.onlyFavorites -> "Favoritos"
                filters.onlySavedOffline -> "Guardados"
                else -> "Todas"
            }
            ChipRow(
                items = listOf("Todas", "Favoritos", "Guardados"),
                isSelected = { it == current },
                label = { it },
                onClick = { sel ->
                    when (sel) {
                        "Favoritos" -> {
                            if (filters.onlySavedOffline) onOnlySavedOffline(false)
                            onOnlyFavorites(true)
                        }
                        "Guardados" -> {
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
        Section("ORDENAR POR") {
            ChipRow(
                items = SortBy.entries,
                isSelected = { it == filters.sortBy },
                label = { it.label },
                onClick = onSort
            )
        }
    }
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

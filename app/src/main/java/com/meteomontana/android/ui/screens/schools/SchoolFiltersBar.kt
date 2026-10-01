package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.util.CatalogLabels
import com.meteomontana.android.util.AppText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

/** N/NE/E/SE/S/SO/O/NO — mismas 8 orientaciones que §8.1. */
private val ORIENTATIONS = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

/**
 * Barra de filtros estilo PWA: secciones apiladas (distancia, estilo, roca,
 * favoritos, ordenar). Cada sección es una fila horizontal scrollable de
 * chips. Gana un selector "Escuelas"/"Vías/Bloques" arriba del todo
 * (BLOCK_SEARCH_DESIGN.md §4.1b) — réplica exacta del diseño validado en
 * iOS tras probar y descartar el modo implícito sin pestaña.
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
    onClearRocks: () -> Unit = {},
    exploreTab: ExploreTab,
    onExploreTab: (ExploreTab) -> Unit,
    gradeMin: String?,
    gradeMax: String?,
    onGradeMin: (String?) -> Unit,
    onGradeMax: (String?) -> Unit,
    orientations: Set<String>,
    onToggleOrientation: (String) -> Unit,
    exploreSortBy: ExploreSortBy,
    onExploreSort: (ExploreSortBy) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExploreTabSwitcher(exploreTab, onExploreTab)
        Section(stringResource(R.string.w_distance_caps)) {
            ChipRow(
                items = DISTANCE_OPTIONS,
                isSelected = { it == filters.maxDistanceKm },
                label = { if (it == null) AppText.get(R.string.w_all) else "${it.toInt()} km" },
                onClick = onDistance
            )
        }
        if (exploreTab == ExploreTab.Blocks) {
            Section(stringResource(R.string.explore_grado)) {
                GradeRangeSection(gradeMin, gradeMax, onGradeMin, onGradeMax)
            }
            Section(stringResource(R.string.explore_orientacion)) {
                ChipRow(
                    items = ORIENTATIONS,
                    isSelected = { it in orientations },
                    label = { it },
                    onClick = onToggleOrientation
                )
            }
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
        // MOSTRAR: solo tiene sentido en la pestaña Escuelas (favoritas/guardadas).
        if (exploreTab == ExploreTab.Schools) {
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
        }
        Section(stringResource(R.string.school_filters_bar_v3_ordenar_por)) {
            if (exploreTab == ExploreTab.Blocks) {
                ChipRow(
                    items = ExploreSortBy.entries,
                    isSelected = { it == exploreSortBy },
                    label = { AppText.get(it.labelRes) },
                    onClick = onExploreSort
                )
            } else {
                ChipRow(
                    items = SortBy.entries,
                    isSelected = { it == filters.sortBy },
                    label = { AppText.get(it.labelRes) },
                    onClick = onSort
                )
            }
        }
    }
}

/** Segmented control "Escuelas"/"Vías/Bloques" en terracota — Álvaro,
 *  2026-10-01: "sale en negro y no en el color de la app". */
@Composable
private fun ExploreTabSwitcher(tab: ExploreTab, onTab: (ExploreTab) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(3.dp)
    ) {
        TabButton(stringResource(R.string.explore_tab_schools), tab == ExploreTab.Schools, Modifier.weight(1f)) { onTab(ExploreTab.Schools) }
        TabButton(stringResource(R.string.explore_tab_blocks), tab == ExploreTab.Blocks, Modifier.weight(1f)) { onTab(ExploreTab.Blocks) }
    }
}

@Composable
private fun TabButton(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) Terra else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            label,
            color = if (active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Dos campos MÍN/MÁX con desplegable de grados — Álvaro, 2026-10-01 (2ª
 *  vuelta): "pulsar y poner un filtro", no un slider ni chips en fila. */
@Composable
private fun GradeRangeSection(
    gradeMin: String?, gradeMax: String?,
    onGradeMin: (String?) -> Unit, onGradeMax: (String?) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GradeDropdown(stringResource(R.string.explore_min), gradeMin, onGradeMin, Modifier.weight(1f))
        GradeDropdown(stringResource(R.string.explore_max), gradeMax, onGradeMax, Modifier.weight(1f))
    }
}

@Composable
private fun GradeDropdown(
    label: String, selected: String?, onPick: (String?) -> Unit, modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(label, style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    selected ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.explore_sin_filtro)) }, onClick = { onPick(null); open = false })
            EXPLORE_GRADE_LADDER.forEach { g ->
                DropdownMenuItem(text = { Text(g) }, onClick = { onPick(g); open = false })
            }
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

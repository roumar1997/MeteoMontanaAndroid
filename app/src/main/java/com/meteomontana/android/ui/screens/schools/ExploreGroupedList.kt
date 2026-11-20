package com.meteomontana.android.ui.screens.schools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.util.ExploreSectors
import com.meteomontana.android.domain.util.SchoolKind
import com.meteomontana.android.ui.theme.Spacing

/** «12 bloques» si todo son bloques, «12 vías» si todo son vías y «12 vías/bloques» si hay mezcla. */
@Composable
internal fun exploreCountText(count: Int, boulders: Boolean, routes: Boolean): String = when {
    boulders -> if (count == 1) stringResource(R.string.explore_group_count_boulders_one)
        else stringResource(R.string.explore_group_count_boulders_other, count)
    routes -> if (count == 1) stringResource(R.string.explore_group_count_routes_one)
        else stringResource(R.string.explore_group_count_routes_other, count)
    count == 1 -> stringResource(R.string.explore_group_count_one)
    else -> stringResource(R.string.explore_group_count_other, count)
}

/**
 * Cabecera plegable de un SECTOR dentro de una escuela: una TARJETA propia (redondeada, con borde y sombra suave), con
 * el nombre, el contador en una pastilla y un chevron en un circulo que se rellena al abrir, para que se vea que se despliega.
 */
@Composable
internal fun ExploreSectorHeader(name: String?, count: Int, boulders: Boolean, routes: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 4.dp)
            .shadow(if (expanded) 0.dp else 2.dp, shape, clip = false)
            .clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name ?: stringResource(R.string.explore_sector_none), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f), maxLines = 2
        )
        Text(
            exploreCountText(count, boulders, routes), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 9.dp, vertical = 3.dp)
        )
        Spacer(Modifier.size(8.dp))
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null,
                tint = if (expanded) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Estado y acciones de la lista agrupada del buscador de Vías/Bloques (escuelas y sectores plegables). */
internal class ExploreGroupedState(
    val expandedGroups: Set<String>,
    val expandedSectors: Set<String>,
    val toggleGroup: (String) -> Unit,
    val toggleSector: (String) -> Unit
)

/**
 * Lista agrupada: una cabecera por escuela (plegada) y, dentro, sus SECTORES plegables (también plegados) cuando la
 * escuela tiene más de uno; si no, las vías directamente. Contadores «bloques» / «vías» según lo que haya de verdad.
 */
internal fun LazyListScope.exploreGroupedItems(
    groups: List<ExploreGroup>,
    state: ExploreGroupedState,
    styleBySchool: Map<String, String?>,
    headerFor: @Composable (ExploreGroup, Boolean, Boolean, Boolean, () -> Unit) -> Unit,
    row: @Composable (LineSearchHit) -> Unit
) {
    groups.forEach { group ->
        val style = styleBySchool[group.schoolId]
        val boulders = ExploreSectors.allBoulders(group.hits, style)
        val routes = ExploreSectors.allRoutes(group.hits, style)
        item(key = "group-${group.schoolId}") {
            headerFor(group, group.schoolId in state.expandedGroups, boulders, routes) { state.toggleGroup(group.schoolId) }
        }
        if (group.schoolId !in state.expandedGroups) return@forEach
        val sectors = ExploreSectors.split(group.hits)
        if (sectors == null) {
            items(group.hits, key = { "line-${it.lineId}-${it.blockId}" }) { h -> row(h) }
        } else sectors.forEach { sector ->
            val sectorKey = "${group.schoolId}|${sector.name.orEmpty()}"
            item(key = "sector-$sectorKey") {
                ExploreSectorHeader(
                    sector.name, sector.hits.size, boulders, routes, sectorKey in state.expandedSectors
                ) { state.toggleSector(sectorKey) }
            }
            if (sectorKey in state.expandedSectors) items(sector.hits, key = { "line-${it.lineId}-${it.blockId}" }) { h -> row(h) }
        }
    }
}

/** «N bloques encontrados» / «N vías encontradas» / «N vías/bloques encontrados» según lo que haya en el resultado. */
@Composable
internal fun exploreFoundText(hits: List<LineSearchHit>, styleBySchool: Map<String, String?>): String {
    val boulders = hits.isNotEmpty() && hits.all { ExploreSectors.allBoulders(listOf(it), styleBySchool[it.schoolId]) }
    val routes = hits.isNotEmpty() && hits.all { ExploreSectors.allRoutes(listOf(it), styleBySchool[it.schoolId]) }
    return when {
        boulders -> stringResource(R.string.explore_found_boulders, hits.size)
        routes -> stringResource(R.string.explore_found_routes, hits.size)
        else -> stringResource(R.string.explore_results_found, hits.size)
    }
}

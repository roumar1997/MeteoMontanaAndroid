package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.UserLocation
import com.meteomontana.android.domain.util.Geo
import com.meteomontana.android.ui.guide.guideTarget
import com.meteomontana.android.ui.theme.Spacing

/** Azul del marcador de parking y verde del de sector: los mismos del mapa. */
private val ParkingBlue = Color(0xFF1A56DB)
private val SectorGreen = Color(0xFF3F6B4A)

/** A partir de aquí se considera que NO estás en la escuela (mismo criterio que el encuadre inicial del mapa). */
private const val KM_TO_SORT_BY_PROXIMITY = 20.0

/**
 * Sectores y parkings ARRIBA del mapa, cada uno en SU fila y SIN rótulos: el símbolo ya lo dice (S = sector,
 * P = parking). Tocar uno lleva el mapa allí. Igual que iOS (`placesRow`, 2026-10-14).
 *
 * Orden por CERCANÍA solo si de verdad estás en la escuela; lejos o sin GPS, alfabético (estable, no cambia con la
 * deriva del GPS ni viéndolo desde casa). SIN distancia en la etiqueta a propósito: el GPS refresca en continuo y el
 * número bailaba delante de los ojos; la cercanía se nota en el ORDEN.
 */
@Composable
internal fun SchoolPlacesRows(
    blocks: List<Block>,
    userLoc: UserLocation?,
    onParking: (Block) -> Unit,
    onSector: (Block) -> Unit
) {
    val restrictions = LocalSchoolRestrictions.current
    val sectors = orderedByProximity(blocks.filter { it.type == "ZONE" }, userLoc)
    val parkings = orderedByProximity(blocks.filter { it.type == "PARKING" }, userLoc)
    if (sectors.isEmpty() && parkings.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).guideTarget("detail_map.places"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (sectors.isNotEmpty()) {
            PlacesLine(sectors, symbol = "S", color = SectorGreen, shape = CircleShape,
                label = { s ->
                    val stones = blocks.count { it.sectorBlockId == s.id }
                    val name = s.name.ifBlank { stringResource(R.string.school_map_view_sector_fallback) }
                    if (stones > 0) "$name · $stones" else name
                },
                badge = { s -> restrictions?.let { RestrictionMarkButton(it.allInSector(s.id)) } },
                onClick = onSector)
        }
        if (parkings.isNotEmpty()) {
            PlacesLine(parkings, symbol = "P", color = ParkingBlue, shape = RoundedCornerShape(5.dp),
                label = { p -> p.name.ifBlank { stringResource(R.string.school_map_view_parking_fallback) } },
                onClick = onParking)
        }
    }
}

@Composable
private fun PlacesLine(
    items: List<Block>,
    symbol: String,
    color: Color,
    shape: androidx.compose.ui.graphics.Shape,
    label: @Composable (Block) -> String,
    badge: @Composable (Block) -> Unit = {},
    onClick: (Block) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = Spacing.md)
    ) {
        items(items, key = { it.id }) { place ->
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onClick(place) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.size(20.dp).clip(shape).background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(symbol, color = Color.White, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
                Text(label(place), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                badge(place)
            }
        }
    }
}

/** Cerca de la escuela → por distancia (lo útil en la roca); lejos o sin GPS → alfabético. */
internal fun orderedByProximity(items: List<Block>, userLoc: UserLocation?): List<Block> {
    if (userLoc == null) return items.sortedBy { it.name }
    val withDistance = items.map { it to Geo.haversineKm(userLoc.lat, userLoc.lon, it.lat, it.lon) }
    val inTheSchool = withDistance.any { it.second <= KM_TO_SORT_BY_PROXIMITY }
    return if (inTheSchool) withDistance.sortedBy { it.second }.map { it.first } else items.sortedBy { it.name }
}

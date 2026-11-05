package com.meteomontana.android.ui.screens.schools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.FavoriteRow
import com.meteomontana.android.domain.model.FavoritesGrid
import com.meteomontana.android.domain.usecase.favorites.GetFavoritesGridUseCase
import com.meteomontana.android.ui.components.ScoreRing
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.scoreColor
import com.meteomontana.android.util.CalendarLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// Comparar tus favoritas desde Escuelas (Álvaro, 2026-10-28, espejo de FavoritesCompareCard.swift): la estrella «FAVS»
// de la fila de FILTROS enciende esta tarjeta encima de la lista. Antes era la tabla «TUS FAVORITAS» del Tiempo.
// Anillo con la nota de hoy y una tira de barras por día con el mejor día recuadrado y dicho con palabras.

@HiltViewModel
class FavoritesCompareViewModel @Inject constructor(
    private val getFavoritesGrid: GetFavoritesGridUseCase
) : ViewModel() {
    private val _grid = MutableStateFlow<FavoritesGrid?>(null)
    val grid: StateFlow<FavoritesGrid?> = _grid.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { getFavoritesGrid() }.onSuccess { _grid.value = it }
            _loading.value = false
        }
    }
}

@Composable
fun FavoritesCompareCard(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm: FavoritesCompareViewModel = hiltViewModel()
    val grid by vm.grid.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Terra, modifier = Modifier.height(11.dp))
            Text(stringResource(R.string.favs_card_title, grid?.days?.size ?: 7),
                style = EyebrowTextStyle.copy(fontSize = 10.5.sp), color = Terra)
        }
        val g = grid
        if (g != null && g.rows.isNotEmpty()) {
            g.rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                FavoriteCompareRow(row, g.days, Modifier.clickable { onOpen(row.schoolId) })
            }
        } else if (!loading) {
            Text(stringResource(R.string.favs_card_empty), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Una favorita: anillo con la nota de hoy, nombre, barras por día (la de hoy en negrita) y su mejor día. */
@Composable
private fun FavoriteCompareRow(row: FavoriteRow, days: List<String>, modifier: Modifier = Modifier) {
    val scores = row.cells.map { it.avgScore }
    val best = scores.indices.maxByOrNull { scores[it] } ?: 0
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScoreRing(scores.firstOrNull())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.schoolName, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
            Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom) {
                scores.forEachIndexed { i, v ->
                    val shape = RoundedCornerShape(3.dp)
                    Box(
                        Modifier.weight(1f)
                            .height((26f * v.coerceAtLeast(12) / 100f).coerceAtLeast(6f).dp)
                            .background(scoreColor(v), shape)
                            .then(if (i == best) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onBackground, shape)
                                  else Modifier)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                scores.indices.forEach { i ->
                    Text(
                        if (i == 0) stringResource(R.string.w_today_caps) else dayShort(days.getOrNull(i)),
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                        style = EyebrowTextStyle.copy(fontSize = 9.5.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal),
                        color = if (i == 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (scores.isNotEmpty()) {
                Text(
                    if (best == 0) stringResource(R.string.favs_card_best_today, scores[best])
                    else stringResource(R.string.favs_card_best_day, dayLong(days.getOrNull(best)), scores[best]),
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "2026-10-30" → "VIE" (mismas etiquetas que el resto de la app). */
private fun dayShort(iso: String?): String = runCatching {
    CalendarLabels.daysShortMonFirst()[LocalDate.parse(iso).dayOfWeek.value - 1].uppercase()
}.getOrDefault("")

/** "2026-10-30" → "viernes" en el idioma de la app. */
private fun dayLong(iso: String?): String = runCatching {
    CalendarLabels.daysLongMonFirst()[LocalDate.parse(iso).dayOfWeek.value - 1].lowercase()
}.getOrDefault(iso.orEmpty())

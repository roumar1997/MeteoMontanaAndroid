package com.meteomontana.android.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.SectorHours
import com.meteomontana.android.domain.usecase.forecast.GetSectorHoursUseCase
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.scoreColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mejor hora por sector de hoy. Se pide al abrir la tarjeta HOY; sin red o sin sectores orientados, no se muestra. */
@HiltViewModel
class SectorHoursViewModel @Inject constructor(private val getSectorHours: GetSectorHoursUseCase) : ViewModel() {
    private val _sectors = MutableStateFlow<List<SectorHours>?>(null)
    val sectors: StateFlow<List<SectorHours>?> = _sectors.asStateFlow()
    private var loadedFor: String? = null

    fun load(schoolId: String) {
        if (loadedFor == schoolId) return
        loadedFor = schoolId
        viewModelScope.launch {
            _sectors.value = runCatching { getSectorHours(schoolId) }.onFailure { loadedFor = null }.getOrNull()
        }
    }
}

/**
 * Tarjeta plegable PROPIA bajo la tarjeta HOY (Álvaro, 2026-10-18). Plegada: el mejor sector de hoy y su franja.
 * Desplegada: cada sector con su orientación, su franja y una tira de 8 a 20 h coloreada por nota (punto = sol).
 */
@Composable
fun SectorHoursSection(schoolId: String, viewModel: SectorHoursViewModel = hiltViewModel()) {
    LaunchedEffect(schoolId) { viewModel.load(schoolId) }
    val sectors by viewModel.sectors.collectAsStateWithLifecycle()
    val list = sectors?.filter { it.bestFrom != null } ?: return
    if (list.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val best = list.first()
    val shape = RoundedCornerShape(14.dp)

    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sector_hours_title), fontFamily = com.meteomontana.android.ui.theme.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                if (!expanded) {
                    Text(stringResource(R.string.sector_hours_best, best.name, best.bestFrom!!, best.bestTo!! + 1),
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { SectorHoursRow(it) }
                Text(stringResource(R.string.sector_hours_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SectorHoursRow(s: SectorHours) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(com.meteomontana.android.util.CatalogLabels.aspect(s.aspect), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Box(Modifier.weight(1f))
            if (s.bestFrom != null && s.bestTo != null) {
                Text(stringResource(R.string.sector_hours_window, s.bestFrom!!, s.bestTo!! + 1),
                    color = MaterialTheme.colorScheme.onSurface, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        // Tira de 8 a 20 h: color = nota de esa hora para ESTA pared; la mejor franja con borde; punto blanco = sol.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            s.hours.forEach { h ->
                val inBest = s.bestFrom != null && h.hour in s.bestFrom!!..s.bestTo!!
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height(if (inBest) 18.dp else 12.dp).clip(RoundedCornerShape(2.dp))
                            .background(scoreColor(h.score).copy(alpha = if (inBest) 1f else 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (h.sun) Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                    }
                    if (h.hour % 2 == 0) {
                        Text("${h.hour}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

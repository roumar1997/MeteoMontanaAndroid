package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.GymStats
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymStatsUseCase
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GymStatsUiState(val stats: GymStats = GymStats(), val topGymName: String? = null)

/**
 * Tarjeta «Rocódromo» del perfil. Los números salen de `shared` ([GetGymStatsUseCase]): primero lo que hay en el
 * móvil (sale al instante y sin red) y después, si el servidor ya tiene las rutas de entrenos, lo mezcla.
 */
@HiltViewModel
class GymStatsViewModel @Inject constructor(
    private val getStats: GetGymStatsUseCase,
    private val getCachedGyms: GetCachedGymsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(build(getStats.cached()))
    val state: StateFlow<GymStatsUiState> = _state

    fun refresh() {
        _state.value = build(getStats.cached())
        viewModelScope.launch { runCatching { getStats.refresh() }.onSuccess { s -> _state.update { build(s) } } }
    }

    private fun build(stats: GymStats) = GymStatsUiState(
        stats = stats,
        topGymName = stats.topGymId?.let { id -> getCachedGyms().firstOrNull { it.id == id }?.name }
    )
}

@Composable
internal fun GymStatsCard(modifier: Modifier = Modifier, viewModel: GymStatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val s = state.stats
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.gym_stats_title).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
        if (s.isEmpty) {
            Text(stringResource(R.string.gym_stats_empty), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(stringResource(R.string.gym_stats_week).uppercase(), s.week.toString(), Modifier.weight(1f))
            StatCell(stringResource(R.string.gym_stats_month).uppercase(), s.month.toString(), Modifier.weight(1f))
            StatCell(stringResource(R.string.gym_stats_year).uppercase(), s.year.toString(), Modifier.weight(1f))
        }
        state.topGymName?.let { name ->
            InfoLine(stringResource(R.string.gym_stats_top), androidx.compose.ui.res.pluralStringResource(R.plurals.gym_stats_top_value, s.topGymVisits, name, s.topGymVisits))
        }
        if (s.streakWeeks > 0) {
            InfoLine(
                stringResource(R.string.gym_stats_streak),
                if (s.streakWeeks == 1) stringResource(R.string.gym_stats_streak_one)
                else stringResource(R.string.gym_stats_streak_many, s.streakWeeks)
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f), maxLines = 2
        )
    }
}

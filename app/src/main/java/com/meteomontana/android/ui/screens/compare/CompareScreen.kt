package com.meteomontana.android.ui.screens.compare

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.usecase.forecast.GetForecastUseCase
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.scoreColor
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CompareUiState {
    data object Loading : CompareUiState
    data class Success(val forecasts: List<Forecast>) : CompareUiState
    data class Error(val message: String) : CompareUiState
}

@HiltViewModel
class CompareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getForecast: GetForecastUseCase
) : ViewModel() {

    private val ids: List<String> =
        checkNotNull(savedStateHandle.get<String>("ids")).split(",").filter { it.isNotBlank() }

    private val _state = MutableStateFlow<CompareUiState>(CompareUiState.Loading)
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                // Los 2-3 forecasts en paralelo.
                val forecasts = coroutineScope {
                    ids.map { id -> async { getForecast(id) } }.map { it.await() }
                }
                CompareUiState.Success(forecasts)
            } catch (t: Throwable) {
                CompareUiState.Error(t.toUserMessage())
            }
        }
    }
}

/** Comparador lado a lado de 2-3 escuelas (long-press en la lista). */
@Composable
fun CompareScreen(
    onBack: () -> Unit,
    onSchoolDetail: (String) -> Unit,
    viewModel: CompareViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Comparar escuelas", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            CompareUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is CompareUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
            }
            is CompareUiState.Success -> Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                s.forecasts.forEach { fc ->
                    CompareColumn(
                        forecast = fc,
                        modifier = Modifier.weight(1f),
                        onDetail = { onSchoolDetail(fc.schoolId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareColumn(forecast: Forecast, modifier: Modifier, onDetail: () -> Unit) {
    val ctx = LocalContext.current
    val cur = forecast.current
    val color = scoreColor(cur.score)

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Nombre (clicable → detalle)
        Text(
            forecast.schoolName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(onClick = onDetail).padding(vertical = Spacing.xs)
        )

        // Score hero
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(color)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Text("${cur.score}", style = MaterialTheme.typography.displaySmall,
                color = androidx.compose.ui.graphics.Color.White)
        }
        Text(cur.scoreLabel.uppercase(), style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs))

        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(Spacing.sm))

        StatRow("TEMP", "${cur.temperature.toInt()}°C")
        StatRow("HUM", "${cur.humidity.toInt()}%")
        StatRow("VIENTO", "${cur.windSpeed.toInt()} km/h")
        StatRow("ROCA", if (cur.dryRock) "SECA" else "MOJADA",
            valueColor = if (cur.dryRock) MaterialTheme.colorScheme.secondary
                         else MaterialTheme.colorScheme.error)
        forecast.bestWindow?.let {
            StatRow("ÓPTIMO", "${it.start}–${it.end}")
        }
        forecast.bestDay?.let {
            StatRow("MEJOR DÍA", it.label.uppercase())
        }

        Spacer(Modifier.height(Spacing.sm))

        // Mini heatmap 16h
        Row(Modifier.fillMaxWidth().height(10.dp)) {
            forecast.hours.take(16).forEach { h ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .padding(horizontal = 0.5.dp)
                        .background(scoreColor(h.score))
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.onBackground, MaterialTheme.shapes.small)
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${forecast.lat},${forecast.lon}"))
                    runCatching { ctx.startActivity(intent) }
                }
                .padding(vertical = Spacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text("CÓMO LLEGAR", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = valueColor)
    }
}

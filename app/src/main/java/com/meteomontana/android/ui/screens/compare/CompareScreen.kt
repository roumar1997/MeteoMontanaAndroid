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
import androidx.compose.foundation.layout.width
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

/** Datos de una escuela ya listos para la tabla de comparación. */
data class CompareItem(
    val schoolId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val score: Int,
    val scoreLabel: String,
    val rockType: String?,
    val distanceKm: Double?,
    val temp: Int,
    val wind: Int,
    val humidity: Int,
    val rainProb: Int,
    val dryRock: Boolean,
    val optimal: String?,
    val bestDay: String?
)

sealed interface CompareUiState {
    data object Loading : CompareUiState
    data class Success(val items: List<CompareItem>) : CompareUiState
    data class Error(val message: String) : CompareUiState
}

@HiltViewModel
class CompareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getForecast: GetForecastUseCase,
    private val getSchoolById: com.meteomontana.android.domain.usecase.schools.GetSchoolByIdUseCase,
    private val locationProvider: com.meteomontana.android.domain.port.LocationProvider
) : ViewModel() {

    private val ids: List<String> =
        checkNotNull(savedStateHandle.get<String>("ids")).split(",").filter { it.isNotBlank() }

    private val _state = MutableStateFlow<CompareUiState>(CompareUiState.Loading)
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                val loc = runCatching { locationProvider.current() }.getOrNull()
                // Forecast + escuela (para tipo de roca) de cada id, en paralelo.
                val items = coroutineScope {
                    ids.map { id ->
                        async {
                            val fc = getForecast(id)
                            val school = runCatching { getSchoolById(id) }.getOrNull()
                            val dist = loc?.let {
                                com.meteomontana.android.domain.util.Geo.haversineKm(it.lat, it.lon, fc.lat, fc.lon)
                            }
                            CompareItem(
                                schoolId = fc.schoolId,
                                name = fc.schoolName,
                                lat = fc.lat, lon = fc.lon,
                                score = fc.current.score,
                                scoreLabel = fc.current.scoreLabel,
                                rockType = school?.rockType,
                                distanceKm = dist,
                                temp = fc.current.temperature.toInt(),
                                wind = fc.current.windSpeed.toInt(),
                                humidity = fc.current.humidity.toInt(),
                                rainProb = fc.current.precipitationProbability,
                                dryRock = fc.current.dryRock,
                                optimal = fc.bestWindow?.let { "${shortHour(it.start)}–${shortHour(it.end)}h" },
                                bestDay = fc.bestDay?.label
                            )
                        }
                    }.map { it.await() }
                }
                CompareUiState.Success(items)
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
            is CompareUiState.Success -> CompareTable(
                items = s.items,
                onSchoolDetail = onSchoolDetail
            )
        }
    }
}

@Composable
private fun CompareTable(items: List<CompareItem>, onSchoolDetail: (String) -> Unit) {
    if (items.isEmpty()) return
    val winner = items.maxByOrNull { it.score }!!

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md)
    ) {
        // Cabecera: la mejor de hoy.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(scoreColor(winner.score).copy(alpha = 0.14f))
                .clickable { onSchoolDetail(winner.schoolId) }
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HOY MEJOR", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(winner.name, style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("  ${winner.score}", style = MaterialTheme.typography.titleLarge,
                        color = scoreColor(winner.score))
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))

        // Cabecera de columnas: nombre + badge de score, pulsable → detalle.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Spacer(Modifier.width(LABEL_W))
            items.forEach { it ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onSchoolDetail(it.schoolId) }
                        .padding(Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(it.name, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center, maxLines = 2)
                    Spacer(Modifier.height(Spacing.xs))
                    Box(
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                            .background(scoreColor(it.score))
                            .padding(horizontal = Spacing.sm, vertical = 2.dp)
                    ) {
                        Text("${it.score}", style = MaterialTheme.typography.titleMedium,
                            color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))

        // Tabla de métricas, en una tarjeta con borde. Filas en bandas alternas y
        // columnas separadas por divisorias verticales para que se lea bien.
        val rows = listOf(
            CompareMetric("ROCA", items.map { it.rockType?.uppercase() ?: "—" },
                items.indices.filter { items[it].dryRock }.toSet()),
            CompareMetric("DISTANCIA", items.map { it.distanceKm?.let { d -> "${d.toInt()} km" } ?: "—" },
                minIndices(items.map { it.distanceKm })),
            CompareMetric("TEMP", items.map { "${it.temp}°" }, emptySet()),
            CompareMetric("VIENTO", items.map { "${it.wind} km/h" },
                minIndices(items.map { it.wind.toDouble() })),
            CompareMetric("HUMEDAD", items.map { "${it.humidity}%" },
                minIndices(items.map { it.humidity.toDouble() })),
            CompareMetric("PROB. LLUVIA", items.map { "${it.rainProb}%" },
                minIndices(items.map { it.rainProb.toDouble() })),
            CompareMetric("ÓPTIMO", items.map { it.optimal ?: "—" }, emptySet()),
            CompareMetric("MEJOR DÍA", items.map { it.bestDay?.uppercase() ?: "—" }, emptySet())
        )
        Column(
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
        ) {
            rows.forEachIndexed { idx, m ->
                CompareRow(m, zebra = idx % 2 == 1)
            }
        }
    }
}

private val LABEL_W = 96.dp

private data class CompareMetric(val label: String, val values: List<String>, val best: Set<Int>)

/** Índices con el valor mínimo (los "mejores") de una lista de valores nullable. */
private fun minIndices(values: List<Double?>): Set<Int> {
    val present = values.mapIndexedNotNull { i, v -> v?.let { i to it } }
    if (present.isEmpty()) return emptySet()
    val min = present.minOf { it.second }
    return present.filter { it.second == min }.map { it.first }.toSet()
}

/** "2026-..T08:00" o "08:00" → "8" (hora sin cero ni minutos, para que quepa). */
private fun shortHour(t: String): String {
    val hhmm = if (t.contains("T")) t.substringAfter("T") else t
    val hh = hhmm.take(2).toIntOrNull() ?: return hhmm.take(5)
    return hh.toString()
}

@Composable
private fun CompareRow(metric: CompareMetric, zebra: Boolean) {
    val bg = if (zebra) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
             else androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(bg).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(metric.label, style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LABEL_W).padding(start = Spacing.sm, top = Spacing.md, bottom = Spacing.md))
        metric.values.forEachIndexed { i, v ->
            androidx.compose.material3.VerticalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
            val best = i in metric.best
            Text(
                v,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (best) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                color = if (best) com.meteomontana.android.ui.theme.Terra
                        else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(vertical = Spacing.md, horizontal = 2.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

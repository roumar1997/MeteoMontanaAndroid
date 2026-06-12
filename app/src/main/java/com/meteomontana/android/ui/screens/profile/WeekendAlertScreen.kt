package com.meteomontana.android.ui.screens.profile

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.dto.WeekendAlertDto
import com.meteomontana.android.data.saved.CachedSchoolsRepository
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeekendAlertUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val notifyDay: Int = 4,        // ISO: 1=lunes .. 7=domingo
    val notifyHour: Int = 20,
    /** Días ISO (1=lunes .. 7=domingo) que se comparan en el aviso. */
    val alertDays: Set<Int> = setOf(5, 6, 7),
    val nearbyMode: Boolean = false,   // false = MIS ESCUELAS, true = POR CERCANÍA
    val radiusKm: Int = 50,
    val selected: List<School> = emptyList(),
    val query: String = "",
    val suggestions: List<School> = emptyList(),
    /** Alerta "ventana óptima hoy" sobre las favoritas. */
    val optimalEnabled: Boolean = false,
    val optimalThreshold: Int = 70,
    val saving: Boolean = false,
    val savedOk: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WeekendAlertViewModel @Inject constructor(
    private val profileApi: KtorProfileApi,
    private val cachedSchools: CachedSchoolsRepository,
    private val locationProvider: com.meteomontana.android.data.location.LocationProvider
) : ViewModel() {

    private val _state = MutableStateFlow(WeekendAlertUiState())
    val state: StateFlow<WeekendAlertUiState> = _state.asStateFlow()

    private var catalog: List<School> = emptyList()

    init {
        viewModelScope.launch {
            catalog = runCatching { cachedSchools.load() }.getOrDefault(emptyList())
            val dto = runCatching { profileApi.getWeekendAlert() }.getOrNull()
            val byId = catalog.associateBy { it.id }
            _state.update {
                it.copy(
                    loading = false,
                    enabled = dto?.enabled ?: false,
                    notifyDay = dto?.notifyDay ?: 4,
                    notifyHour = dto?.notifyHour ?: 20,
                    alertDays = dto?.alertDays?.toSet()?.ifEmpty { setOf(5, 6, 7) } ?: setOf(5, 6, 7),
                    nearbyMode = dto?.mode.equals("NEARBY", ignoreCase = true),
                    radiusKm = dto?.radiusKm ?: 50,
                    selected = dto?.schoolIds.orEmpty().mapNotNull { id -> byId[id] },
                    optimalEnabled = dto?.optimalEnabled ?: false,
                    optimalThreshold = dto?.optimalThreshold ?: 70
                )
            }
        }
    }

    fun setEnabled(v: Boolean)  { _state.update { it.copy(enabled = v, savedOk = false) } }
    fun setOptimalEnabled(v: Boolean) { _state.update { it.copy(optimalEnabled = v, savedOk = false, error = null) } }
    fun setOptimalThreshold(t: Int)   { _state.update { it.copy(optimalThreshold = t, savedOk = false) } }
    fun setDay(d: Int)          { _state.update { it.copy(notifyDay = d, savedOk = false) } }
    fun setHour(h: Int)         { _state.update { it.copy(notifyHour = h, savedOk = false) } }

    fun toggleAlertDay(d: Int) {
        _state.update {
            val days = if (d in it.alertDays) it.alertDays - d else it.alertDays + d
            it.copy(alertDays = days, savedOk = false, error = null)
        }
    }
    fun setNearby(v: Boolean)   { _state.update { it.copy(nearbyMode = v, savedOk = false, error = null) } }
    fun setRadius(km: Int)      { _state.update { it.copy(radiusKm = km, savedOk = false) } }

    fun setQuery(q: String) {
        val needle = q.trim().lowercase()
        val sugg = if (needle.length < 2) emptyList() else catalog
            .filter { it.name.lowercase().contains(needle) }
            .filter { c -> _state.value.selected.none { it.id == c.id } }
            .take(5)
        _state.update { it.copy(query = q, suggestions = sugg) }
    }

    fun addSchool(s: School) {
        if (_state.value.selected.size >= 3) return
        _state.update {
            it.copy(selected = it.selected + s, query = "", suggestions = emptyList(), savedOk = false)
        }
    }

    fun removeSchool(id: String) {
        _state.update { it.copy(selected = it.selected.filterNot { s -> s.id == id }, savedOk = false) }
    }

    fun save() {
        val s = _state.value
        // Las escuelas/días solo son obligatorios si la alerta de tiempo está
        // activa: se puede guardar solo el toggle de "ventana óptima hoy".
        if (s.enabled && !s.nearbyMode && s.selected.isEmpty()) {
            _state.update { it.copy(error = "Elige al menos una escuela") }
            return
        }
        if (s.enabled && s.alertDays.isEmpty()) {
            _state.update { it.copy(error = "Elige al menos un día a comparar") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            // En modo cercanía mandamos tu posición actual (el job la usa como centro).
            val loc = if (s.nearbyMode) runCatching { locationProvider.current() }.getOrNull() else null
            if (s.nearbyMode && loc == null) {
                _state.update { it.copy(saving = false,
                    error = "No pudimos obtener tu ubicación — concede el permiso e inténtalo de nuevo") }
                return@launch
            }
            runCatching {
                profileApi.updateWeekendAlert(
                    WeekendAlertDto(
                        enabled = s.enabled, notifyDay = s.notifyDay, notifyHour = s.notifyHour,
                        schoolIds = if (s.nearbyMode) emptyList() else s.selected.map { it.id },
                        mode = if (s.nearbyMode) "NEARBY" else "SCHOOLS",
                        radiusKm = if (s.nearbyMode) s.radiusKm else null,
                        lat = loc?.lat, lon = loc?.lon,
                        alertDays = s.alertDays.sorted(),
                        optimalEnabled = s.optimalEnabled,
                        optimalThreshold = s.optimalThreshold
                    )
                )
            }.onSuccess {
                _state.update { it.copy(saving = false, savedOk = true) }
            }.onFailure { t ->
                _state.update { it.copy(saving = false, error = t.toUserMessage()) }
            }
        }
    }
}

private val DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")
private val HOUR_OPTIONS = listOf(7, 8, 9, 10, 20, 21)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekendAlertScreen(
    onBack: () -> Unit,
    viewModel: WeekendAlertViewModel = hiltViewModel()
) {
    val s by viewModel.state.collectAsState()

    // Al guardar con éxito, volvemos atrás (breve pausa para que se vea el ✓).
    androidx.compose.runtime.LaunchedEffect(s.savedOk) {
        if (s.savedOk) {
            kotlinx.coroutines.delay(600)
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Alerta de tiempo", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (s.loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg)
        ) {
            Text(
                "Te enviamos una notificación comparando hasta 3 escuelas para los " +
                "días que elijas de la próxima semana: nota global, desglose por día " +
                "y aviso de lluvia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.lg))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ACTIVADA", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                Switch(checked = s.enabled, onCheckedChange = viewModel::setEnabled)
            }

            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(Spacing.lg))

            Text("VENTANA ÓPTIMA HOY", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Te avisamos por la mañana (7-11h) si alguna de tus escuelas " +
                "favoritas supera hoy el umbral en su mejor franja de horas. " +
                "Máximo un aviso al día.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ACTIVADA", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                Switch(checked = s.optimalEnabled, onCheckedChange = viewModel::setOptimalEnabled)
            }
            if (s.optimalEnabled) {
                Spacer(Modifier.height(Spacing.sm))
                Text("UMBRAL DE ÍNDICE", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf(60, 70, 80).forEach { t ->
                        SelectChip("$t", selected = s.optimalThreshold == t) {
                            viewModel.setOptimalThreshold(t)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(Spacing.lg))
            Text("QUÉ DÍAS COMPARAR", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            // Próximos 7 días empezando hoy, con su fecha. Se guarda el día de la
            // semana, así la alerta se repite cada semana con esos días.
            val today = java.time.LocalDate.now()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                (0..6).forEach { offset ->
                    val date = today.plusDays(offset.toLong())
                    val iso = date.dayOfWeek.value
                    val label = "${DAY_LABELS[iso - 1]} ${date.dayOfMonth}"
                    SelectChip(label, selected = iso in s.alertDays) { viewModel.toggleAlertDay(iso) }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Marca los días que te interesan (hoy es ${DAY_LABELS[today.dayOfWeek.value - 1]} " +
                "${today.dayOfMonth}). El aviso comparará esos días cada semana.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.lg))
            Text("DÍA DEL AVISO", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DAY_LABELS.forEachIndexed { i, label ->
                    SelectChip(label, selected = s.notifyDay == i + 1) { viewModel.setDay(i + 1) }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Text("HORA", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                HOUR_OPTIONS.forEach { h ->
                    SelectChip("${h}h", selected = s.notifyHour == h) { viewModel.setHour(h) }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Text("QUÉ COMPARAR", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SelectChip("MIS ESCUELAS", selected = !s.nearbyMode) { viewModel.setNearby(false) }
                SelectChip("POR CERCANÍA", selected = s.nearbyMode) { viewModel.setNearby(true) }
            }

            Spacer(Modifier.height(Spacing.lg))
            if (s.nearbyMode) {
                Text("RADIO DESDE TU UBICACIÓN", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf(25, 50, 100, 200).forEach { km ->
                        SelectChip("$km km", selected = s.radiusKm == km) { viewModel.setRadius(km) }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Compararemos las 3 mejores escuelas dentro del radio, " +
                    "desde tu posición al guardar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!s.nearbyMode) {
            Text("ESCUELAS A COMPARAR (MÁX 3)", style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                s.selected.forEach { school ->
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { viewModel.removeSchool(school.id) }
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(school.name, style = MaterialTheme.typography.labelLarge, color = Color.White)
                        Text("  ✕", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }

            if (s.selected.size < 3) {
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = s.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar escuela…") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                s.suggestions.forEach { sugg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.addSchool(sugg) }
                            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sugg.name, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f))
                        sugg.region?.let {
                            Text(it.uppercase(), style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                }
            }
            }   // fin modo MIS ESCUELAS

            Spacer(Modifier.height(Spacing.xl))

            s.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(Spacing.sm))
            }
            if (s.savedOk) {
                Text("✓ Guardado", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(Spacing.sm))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !s.saving) { viewModel.save() }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(if (s.saving) "GUARDANDO…" else "GUARDAR",
                    style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.surface
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(label, style = EyebrowTextStyle,
            color = if (selected) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onSurface)
    }
}

package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymFilters
import com.meteomontana.android.domain.model.gym.GymListItem
import com.meteomontana.android.domain.model.gym.GymOrigin
import com.meteomontana.android.domain.model.gym.GymTrainingPeriod
import com.meteomontana.android.domain.usecase.gym.FilterGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymFavoritesUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GymTrainingUseCase
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import com.meteomontana.android.ui.components.rememberUserLocation
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.terraFillColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import javax.inject.Inject

/** Un rocódromo del selector con lo que se enseña debajo del nombre (distancia, «tu rocódromo» o «hace 2 semanas»). */
data class PickerEntry(val gym: Gym, val distanceKm: Double?, val daysAgo: Int? = null, val isTop: Boolean = false)

data class GymPickerState(
    val query: String = "",
    val results: List<PickerEntry> = emptyList(),
    val favorites: List<PickerEntry> = emptyList(),
    val recents: List<PickerEntry> = emptyList(),
    val nearby: List<PickerEntry> = emptyList()
)

/** Selector de rocódromo para apuntar un entreno: favoritos, recientes y cercanos arriba; con texto, los que coinciden. */
@HiltViewModel
class GymPickerViewModel @Inject constructor(
    getCached: GetCachedGymsUseCase,
    private val getGyms: GetGymsUseCase,
    private val getFavorites: GetGymFavoritesUseCase,
    private val training: GymTrainingUseCase,
    private val filterGyms: FilterGymsUseCase
) : ViewModel() {
    private var all: List<Gym> = getCached()
    private var origin: GymOrigin? = null
    private var favoriteIds: Set<Long> = emptySet()
    private val _state = MutableStateFlow(GymPickerState())
    val state: StateFlow<GymPickerState> = _state

    init {
        recompute()
        viewModelScope.launch {
            runCatching { getGyms.execute() }.onSuccess { all = it; recompute() }
            runCatching { getFavorites.execute() }.onSuccess { favoriteIds = it; recompute() }
        }
    }

    fun setOrigin(lat: Double?, lon: Double?) { origin = if (lat != null && lon != null) GymOrigin(lat, lon) else null; recompute() }

    fun search(q: String) { _state.update { it.copy(query = q) }; recompute() }

    private fun recompute() {
        val q = _state.value.query
        val items = filterGyms(all, GymFilters(query = q), origin, favoriteIds)
        if (q.isNotBlank()) { _state.update { it.copy(results = items.take(20).map(::entry)) }; return }
        val byId = items.associateBy { it.gym.id }
        val today = training.today()
        val sessions = training.summary(GymTrainingPeriod.YEAR, today).sessions
        val topId = sessions.groupingBy { it.gymId }.eachCount().maxByOrNull { it.value }?.key
        val favs = favoriteIds.mapNotNull { byId[it] }.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        val recents = sessions.sortedByDescending { it.date }.distinctBy { it.gymId }
            .filter { it.gymId !in favoriteIds }.take(4)
            .mapNotNull { s -> byId[s.gymId]?.let { PickerEntry(it.gym, it.distanceKm, daysAgo = s.date.daysUntil(today), isTop = it.gym.id == topId) } }
        val used = favs.map { it.gym.id }.toSet() + recents.map { it.gym.id }.toSet()
        val nearby = items.filter { it.gym.id !in used }.take(6)
        _state.update { s ->
            s.copy(
                results = emptyList(), favorites = favs.map { entry(it, it.gym.id == topId) }, recents = recents,
                nearby = nearby.map(::entry)
            )
        }
    }

    private fun entry(i: GymListItem, top: Boolean = false) = PickerEntry(i.gym, i.distanceKm, isTop = top)
}

private enum class PickDay { TODAY, YESTERDAY, OTHER }

/**
 * «¿Dónde has entrenado?» (maqueta 3): hoja con buscador, día (Hoy / Ayer / Otro día) y los rocódromos favoritos,
 * recientes y cercanos arriba. Al elegir uno y pulsar «Seguir» devuelve el rocódromo y el día.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymPickerSheet(
    onPick: (Gym, LocalDate) -> Unit, onDismiss: () -> Unit,
    /** Si se pasa, arriba sale «Solo board» (boards sin rocódromo) y devuelve solo el día elegido. */
    onSoloBoard: ((LocalDate) -> Unit)? = null, vm: GymPickerViewModel = hiltViewModel()
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val loc = rememberUserLocation()
    LaunchedEffect(loc?.lat, loc?.lon) { vm.setOrigin(loc?.lat, loc?.lon) }
    var chosen by remember { mutableStateOf<Gym?>(null) }
    var day by remember { mutableStateOf(PickDay.TODAY) }
    var other by remember { mutableStateOf<LocalDate?>(null) }
    var datePicking by remember { mutableStateOf(false) }
    val today = remember { java.time.LocalDate.now() }
    val date: LocalDate = when (day) {
        PickDay.TODAY -> LocalDate(today.year, today.monthValue, today.dayOfMonth)
        PickDay.YESTERDAY -> today.minusDays(1).let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
        PickDay.OTHER -> other ?: LocalDate(today.year, today.monthValue, today.dayOfMonth)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent, shape = CumbreSheetShape
    ) {
        Column(Modifier.fillMaxWidth().cumbreSheetSurface(MaterialTheme.colorScheme.background).padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.gym_pick_eyebrow).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.gym_pick_title), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(com.meteomontana.android.core.ui.R.string.common_close)) }
            }
            TextField(
                value = st.query, onValueChange = vm::search, singleLine = true, shape = CircleShape,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text(stringResource(R.string.gym_search_hint), fontSize = 14.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            )
            Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayChip(stringResource(R.string.gym_pick_today), day == PickDay.TODAY) { day = PickDay.TODAY }
                DayChip(stringResource(R.string.gym_pick_yesterday), day == PickDay.YESTERDAY) { day = PickDay.YESTERDAY }
                DayChip(
                    other?.takeIf { day == PickDay.OTHER }?.let { "${it.dayOfMonth}/${it.monthNumber}" } ?: stringResource(R.string.gym_pick_other_day),
                    day == PickDay.OTHER, icon = true
                ) { datePicking = true }
            }
            Column(
                Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(top = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                onSoloBoard?.let { solo ->
                    SoloBoardRow { solo(date) }
                    // Boards is its own path (opens the log at once): a rule keeps it apart from the gym list.
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                }
                if (st.query.isNotBlank()) st.results.forEach { PickRow(it, it.gym.id == chosen?.id) { chosen = it.gym } }
                else {
                    PickSection(stringResource(R.string.gym_pick_favorites), st.favorites, chosen) { chosen = it }
                    PickSection(stringResource(R.string.gym_pick_recent), st.recents, chosen) { chosen = it }
                    PickSection(stringResource(R.string.gym_pick_near), st.nearby, chosen) { chosen = it }
                }
            }
            val c = chosen
            Box(
                Modifier.fillMaxWidth().padding(top = Spacing.sm).clip(CircleShape).background(terraFillColor().copy(alpha = if (c != null) 1f else 0.4f))
                    .clickable(enabled = c != null) { c?.let { onPick(it, date) } }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (if (c != null) stringResource(R.string.gym_pick_continue, c.name) else stringResource(R.string.gym_pick_choose)).uppercase(),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1
                )
            }
        }
    }
    if (datePicking) {
        val pickerState = rememberDatePickerState(selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
        })
        DatePickerDialog(
            onDismissRequest = { datePicking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        other = LocalDate(d.year, d.monthValue, d.dayOfMonth); day = PickDay.OTHER
                    }
                    datePicking = false
                }) { Text(stringResource(com.meteomontana.android.core.ui.R.string.common_save).uppercase()) }
            },
            dismissButton = { TextButton(onClick = { datePicking = false }) { Text(stringResource(R.string.common_cancel).uppercase()) } }
        ) { DatePicker(pickerState) }
    }
}

/** «Boards»: boards sin rocódromo (tablero de casa); al tocarla se abre el registro de boards del día elegido. */
@Composable
private fun SoloBoardRow(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, terraFillColor(), shape).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.GridView, null, tint = Color.White, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(terraFillColor()).padding(9.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.gym_solo_board_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.gym_solo_board_sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayChip(text: String, on: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, CircleShape)
            .border(if (on) 2.dp else 1.dp, if (on) terraFillColor() else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon) Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(text.uppercase(), style = EyebrowTextStyle, color = if (on) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PickSection(title: String, items: List<PickerEntry>, chosen: Gym?, onPick: (Gym) -> Unit) {
    if (items.isEmpty()) return
    Text(title.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    items.forEach { e -> PickRow(e, e.gym.id == chosen?.id) { onPick(e.gym) } }
}

@Composable
private fun PickRow(e: PickerEntry, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) terraFillColor() else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(terraFillColor()), contentAlignment = Alignment.Center) {
            Text(e.gym.name.take(1).uppercase(), color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(e.gym.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
            val sub = listOfNotNull(
                GymPresentation.distanceText(e.distanceKm),
                if (e.isTop) stringResource(R.string.gym_pick_yours) else null,
                e.daysAgo?.let { agoText(it) },
                if (e.distanceKm == null && e.daysAgo == null) listOf(e.gym.city, e.gym.province).filter { it.isNotBlank() }.distinct().joinToString(", ").ifEmpty { null } else null
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Outlined.Check, null, tint = terraFillColor())
        else if (e.isTop) Icon(Icons.Filled.Star, null, tint = terraFillColor(), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun agoText(days: Int): String = when {
    days <= 0 -> stringResource(R.string.gym_pick_today).lowercase()
    days < 14 -> pluralStringResource(R.plurals.gym_pick_ago_days, days, days)
    days < 60 -> pluralStringResource(R.plurals.gym_pick_ago_weeks, days / 7, days / 7)
    else -> pluralStringResource(R.plurals.gym_pick_ago_months, days / 30, days / 30)
}

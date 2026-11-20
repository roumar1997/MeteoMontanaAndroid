package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymFilters
import com.meteomontana.android.domain.usecase.gym.FilterGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymsUseCase
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.screens.gyms.SegmentedPair
import com.meteomontana.android.ui.screens.schools.GradeRangeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Campos de «Crear quedada» para rocódromos: Escuela | Rocódromo, buscador de rocódromos, grado orientativo y cordada.

private const val MAX_RESULTS = 50

/** Buscador del catálogo de rocódromos para el formulario (copia local al instante, luego la red). */
@HiltViewModel
class MeetupGymPickerViewModel @Inject constructor(
    getCached: GetCachedGymsUseCase,
    getGyms: GetGymsUseCase,
    private val filterGyms: FilterGymsUseCase
) : ViewModel() {
    private val all = MutableStateFlow(getCached())
    private val query = MutableStateFlow("")

    val results: StateFlow<List<Gym>> = combine(all, query) { gyms, q ->
        filterGyms(gyms, GymFilters(query = q)).map { it.gym }.take(MAX_RESULTS)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { runCatching { getGyms.execute() }.onSuccess { all.value = it } }
    }

    fun search(q: String) { query.value = q }
}

/** Segmento «Escuela | Rocódromo» del formulario. */
@Composable
internal fun MeetupPlaceKindSelector(isGym: Boolean, onChange: (Boolean) -> Unit) {
    SegmentedPair(
        first = stringResource(R.string.create_meetup_place_school).uppercase(),
        second = stringResource(R.string.create_meetup_place_gym).uppercase(),
        firstSelected = !isGym, onFirst = { onChange(false) }, onSecond = { onChange(true) }
    )
}

/** Grado orientativo (rango opcional) y, si la quedada es de vía, «Cordada (1 persona)». */
@Composable
internal fun MeetupGradeAndRopeTeam(
    gradeMin: String?, gradeMax: String?,
    onGradeMin: (String?) -> Unit, onGradeMax: (String?) -> Unit,
    isRoute: Boolean, ropeTeam: Boolean, onRopeTeam: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.create_meetup_grade_label), style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GradeRangeSection(gradeMin, gradeMax, onGradeMin, onGradeMax)
        if (isRoute) CumbreChip(stringResource(R.string.create_meetup_rope_team), ropeTeam, { onRopeTeam(!ropeTeam) })
    }
}

/** Diálogo de búsqueda de rocódromo: nombre, ciudad o provincia, sin tildes. */
@Composable
internal fun MeetupGymPickerDialog(
    onSelect: (Gym) -> Unit,
    onDismiss: () -> Unit,
    viewModel: MeetupGymPickerViewModel = hiltViewModel()
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_meetup_gym_search_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it; viewModel.search(it) },
                    placeholder = { Text(stringResource(R.string.gym_search_hint)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
                )
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(results, key = { it.id }) { gym ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(gym) }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                            Text(gym.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOf(gym.city, gym.province).filter { it.isNotBlank() }.distinct().joinToString(", "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

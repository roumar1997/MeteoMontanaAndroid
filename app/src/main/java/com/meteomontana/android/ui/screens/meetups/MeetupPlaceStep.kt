package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.MeetupDraft
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.components.CumbreSearchField
import com.meteomontana.android.ui.components.rememberUserLocation
import com.meteomontana.android.ui.screens.gyms.GymPresentation
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.scoreColor
import com.meteomontana.android.ui.theme.terraFillColor
import com.meteomontana.android.util.CalendarLabels

/** Paso 1 de 3: ¿Dónde vais a escalar? Escuela | Rocódromo, buscador y días. */
@Composable
internal fun MeetupPlaceStep(
    draft: MeetupDraft,
    onDraft: (MeetupDraft) -> Unit,
    schoolResults: List<School>,
    onSchoolQuery: (String) -> Unit,
    dayScores: Map<String, Int>,
    gymPicker: MeetupGymPickerViewModel = hiltViewModel()
) {
    var query by rememberSaveable { mutableStateOf("") }
    val gyms by gymPicker.results.collectAsStateWithLifecycle()
    val loc = rememberUserLocation()
    LaunchedEffect(loc?.lat, loc?.lon) { gymPicker.setOrigin(loc?.lat, loc?.lon) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WizardQuestion(stringResource(R.string.mw_step1_q))
        WizardSegmented(
            first = stringResource(R.string.create_meetup_place_school), second = stringResource(R.string.create_meetup_place_gym),
            firstSelected = !draft.placeIsGym,
            onFirst = { query = ""; onSchoolQuery(""); gymPicker.search(""); onDraft(draft.withPlaceKind(false)) },
            onSecond = { query = ""; onSchoolQuery(""); gymPicker.search(""); onDraft(draft.withPlaceKind(true)) }
        )
        CumbreSearchField(
            value = query,
            onValueChange = { query = it; if (draft.placeIsGym) gymPicker.search(it) else onSchoolQuery(it) },
            placeholder = stringResource(if (draft.placeIsGym) R.string.gym_search_hint else R.string.mw_place_school_hint)
        )
        // El lugar ya elegido (también el que viene de «Crear quedada aquí» o del asistente) se ve siempre.
        if (draft.hasPlace) {
            WizardEyebrow(stringResource(R.string.create_meetup_place_school).takeIf { !draft.placeIsGym }
                ?: stringResource(R.string.create_meetup_place_gym))
            // Tocarlo lo quita para elegir otro.
            PlaceCard(draft.placeName, subtitle = null, gym = draft.placeIsGym, selected = true, onClick = {
                onDraft(draft.copy(schoolId = "", gymId = null, placeName = ""))
            })
        }
        // Con un lugar elegido se pliegan los resultados (solo si se vuelve a buscar) para que los días queden a la vista.
        val showResults = !draft.hasPlace || query.isNotBlank()
        if (!showResults) Unit
        else if (draft.placeIsGym) {
            if (gyms.isNotEmpty()) {
                WizardEyebrow(stringResource(if (query.isBlank() && loc != null) R.string.mw_nearby else R.string.mw_results))
                gyms.filter { it.gym.id != draft.gymId }.forEach { item ->
                    val g = item.gym
                    val sub = listOfNotNull(
                        listOf(g.city, g.province).filter { it.isNotBlank() }.distinct().joinToString(", ").ifEmpty { null },
                        GymPresentation.distanceText(item.distanceKm)
                    ).joinToString(" · ")
                    PlaceCard(g.name, sub.ifEmpty { null }, gym = true, selected = false, onClick = { query = ""; gymPicker.search(""); onDraft(draft.withGym(g.id, g.name)) })
                }
            } else if (query.isNotBlank()) {
                Text(stringResource(R.string.mw_no_results), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            if (schoolResults.isNotEmpty()) {
                WizardEyebrow(stringResource(R.string.mw_results))
                schoolResults.distinctBy { it.id }.filter { it.id != draft.schoolId }.forEach { s ->
                    PlaceCard(s.name, s.location, gym = false, selected = false, onClick = { query = ""; onSchoolQuery(""); onDraft(draft.withSchool(s.id, s.name)) })
                }
            } else if (query.length >= 2) {
                Text(stringResource(R.string.mw_no_results), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        WizardEyebrow(stringResource(R.string.mw_when))
        MeetupDayPicker(draft.days, dayScores) { onDraft(draft.toggleDay(it)) }
    }
}

@Composable
private fun PlaceCard(name: String, subtitle: String?, gym: Boolean, selected: Boolean, onClick: () -> Unit) {
    WizardOptionCard(selected, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (gym) MaterialTheme.colorScheme.onSurface else terraFillColor()),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    painterResource(R.drawable.ic_gym), null,
                    tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(22.dp)
                )
            }
            WizardCardTitle(name, subtitle)
        }
    }
}

/** Segmento «A | B» de ancho completo (20 dp de margen del asistente, no el de 12 de la lista). */
@Composable
internal fun WizardSegmented(first: String, second: String, firstSelected: Boolean, onFirst: () -> Unit, onSecond: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), CircleShape).padding(4.dp)
    ) {
        SegmentCell(first, firstSelected, Modifier.weight(1f), onFirst)
        SegmentCell(second, !firstSelected, Modifier.weight(1f), onSecond)
    }
}

@Composable
private fun SegmentCell(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(CircleShape).background(if (selected) terraFillColor() else Color.Transparent, CircleShape)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(), style = EyebrowTextStyle, maxLines = 1,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Próximos 14 días en tarjetas redondeadas (varios a la vez); con escuela, la nota del día debajo. */
@Composable
internal fun MeetupDayPicker(selected: Set<String>, scores: Map<String, Int>, onToggle: (String) -> Unit) {
    val today = java.util.Calendar.getInstance()
    val names = CalendarLabels.daysShortSunFirst().map { it.uppercase() }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until 14).forEach { i ->
            val cal = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, i) }
            val iso = "%04d-%02d-%02d".format(
                cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
            val on = iso in selected
            val label = if (i == 0) stringResource(R.string.w_today).uppercase() else names[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.width(58.dp).clip(shape)
                    .background(if (on) terraFillColor() else MaterialTheme.colorScheme.surface, shape)
                    .border(1.dp, if (on) terraFillColor() else MaterialTheme.colorScheme.outline, shape)
                    .clickable { onToggle(iso) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(label, style = EyebrowTextStyle, maxLines = 1, color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    cal.get(java.util.Calendar.DAY_OF_MONTH).toString(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurface
                )
                scores[iso]?.let { s ->
                    Box(Modifier.padding(top = 2.dp).clip(CircleShape).background(scoreColor(s)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text("$s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

package com.meteomontana.android.ui.screens.meetups

import com.meteomontana.android.ui.meetups.StepperButton
import com.meteomontana.android.ui.meetups.gearLabelRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.MeetupDraft
import com.meteomontana.android.domain.model.MeetupGearItem
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.gradeChipStyle
import com.meteomontana.android.ui.theme.terraFillColor


/**
 * Paso 2 de 3: ¿Qué vais a hacer? Bloque y Vía son casillas (se pueden marcar las dos), el grado del grupo es un rango
 * sobre la escala con los colores de la app y, debajo, cuántos buscáis y la cordada. Todo entra sin scroll.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetupDetailsStep(draft: MeetupDraft, onDraft: (MeetupDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WizardQuestion(stringResource(R.string.mw_step2_q))
        WizardEyebrow(stringResource(R.string.mw_modality_both))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WizardOptionCard(draft.hasBoulder, { onDraft(draft.toggleBoulder()) }, Modifier.weight(1f)) {
                WizardCardTitle(stringResource(R.string.mw_boulder_title), stringResource(R.string.mw_boulder_sub), serif = true)
            }
            WizardOptionCard(draft.hasRoute, { onDraft(draft.toggleRoute()) }, Modifier.weight(1f)) {
                WizardCardTitle(stringResource(R.string.mw_route_title), stringResource(R.string.mw_route_sub), serif = true)
            }
        }

        val range = draft.gradeMin?.let { min ->
            val max = draft.gradeMax ?: min
            if (min == max) min.uppercase() else stringResource(R.string.mw_grade_range, min.uppercase(), max.uppercase())
        }
        WizardEyebrow(stringResource(R.string.mw_grade_group) + (range?.let { " · $it" } ?: ""))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MeetupDraft.GRADE_LADDER.forEach { g -> GradeChoiceChip(g, draft.isGradeSelected(g)) { onDraft(draft.toggleGrade(g)) } }
        }

        WizardEyebrow(stringResource(R.string.mw_partners_q))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WizardStepper(
                value = draft.partners.toString(), unit = pluralStringResource(R.plurals.mw_people, draft.partners), modifier = Modifier.weight(1f),
                onMinus = if (draft.partners > MeetupDraft.MIN_PARTNERS) ({ onDraft(draft.withPartners(draft.partners - 1)) }) else null,
                onPlus = if (draft.partners < MeetupDraft.MAX_PARTNERS) ({ onDraft(draft.withPartners(draft.partners + 1)) }) else null
            )
            CumbreChip(stringResource(R.string.create_meetup_rope_team), draft.isRopeTeam, { onDraft(draft.toggleRopeTeam()) })
        }
        if (draft.isRopeTeam) Text(stringResource(R.string.mw_rope_team_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Chip de grado con SU color de la app; dentro del rango lleva un aro terracota. Se toca el extremo de abajo y el de arriba. */
@Composable
private fun GradeChoiceChip(grade: String, selected: Boolean, onClick: () -> Unit) {
    val st = gradeChipStyle(grade)
    val shape = RoundedCornerShape(12.dp)
    Text(
        grade.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
        color = if (st.dark) Color(0xFF1B1B1B) else Color.White,
        modifier = Modifier.clip(shape).background(st.stroke, shape)
            .border(if (selected) 3.dp else 0.dp, if (selected) terraFillColor() else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 9.dp)
    )
}

/**
 * Material mínimo del paso 3: baldosas de dos en dos. Con cantidad (cintas, crashpads) la baldosa lleva − n + debajo.
 * Se ofrece solo el material que tiene sentido según el lugar y la modalidad.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GearTiles(draft: MeetupDraft, onDraft: (MeetupDraft) -> Unit) {
    val items = MeetupGearItem.forPlace(draft.discipline, draft.placeIsGym)
    if (items.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2
    ) {
        items.forEach { item ->
            val qty = draft.gear[item]
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.weight(1f).clip(shape)
                    .background(if (qty != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
                    .border(if (qty != null) 2.dp else 1.dp, if (qty != null) terraFillColor() else MaterialTheme.colorScheme.outline, shape)
                    .clickable { onDraft(draft.toggleGear(item)) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(gearLabelRes(item)), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                if (qty != null && MeetupGearItem.hasQuantity(item)) Row(verticalAlignment = Alignment.CenterVertically) {
                    StepperButton("−", if (qty > 1) ({ onDraft(draft.withGearQty(item, qty - 1)) }) else null, primary = false, description = "−", size = 30)
                    Text(qty.toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 10.dp))
                    StepperButton("+", if (qty < MeetupGearItem.MAX_QTY) ({ onDraft(draft.withGearQty(item, qty + 1)) }) else null, primary = true, description = "+", size = 30)
                }
            }
        }
    }
}

/** Resumen oscuro: lugar · días y, debajo, modalidad · personas · grado. */
@Composable
internal fun MeetupSummaryCard(draft: MeetupDraft) {
    val shape = RoundedCornerShape(20.dp)
    val discipline = when (draft.discipline) {
        "BOULDER" -> stringResource(R.string.mw_boulder_title)
        "ROUTE" -> stringResource(R.string.mw_route_title)
        "BOTH" -> stringResource(R.string.mw_both_title)
        else -> null
    }
    val grade = draft.gradeMin?.let { min ->
        val max = draft.gradeMax ?: min
        if (min == max) min.uppercase() else "${min.uppercase()}–${max.uppercase()}"
    }
    val line1 = listOf(draft.placeName, draft.days.sorted().joinToString(", ") { it.takeLast(5).split("-").reversed().joinToString("/") })
        .filter { it.isNotBlank() }.joinToString(" · ")
    val line2 = listOfNotNull(discipline, "${draft.partners} " + pluralStringResource(R.plurals.mw_people, draft.partners), grade).joinToString(" · ")
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF1B1B1B), shape).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.mw_summary).uppercase(), style = EyebrowTextStyle, color = Color(0xFFBDB7A6))
        Text(line1, fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFFF5F3EE))
        Text(line2, fontSize = 13.sp, color = Color(0xFFDDD7C6))
    }
}

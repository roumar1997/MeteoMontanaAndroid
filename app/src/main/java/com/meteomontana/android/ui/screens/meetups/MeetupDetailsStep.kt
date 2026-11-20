package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.meteomontana.android.ui.theme.terraFillColor

internal fun gearLabelRes(item: String): Int = when (item) {
    MeetupGearItem.ROPE -> R.string.gear_rope
    MeetupGearItem.GRIGRI -> R.string.gear_grigri
    MeetupGearItem.QUICKDRAWS -> R.string.gear_quickdraws
    else -> R.string.gear_crashpad
}

/** Paso 2 de 3: ¿Qué vais a hacer? Bloque/Vía, cuántos buscáis, grado orientativo, material mínimo y resumen. */
@Composable
internal fun MeetupDetailsStep(draft: MeetupDraft, onDraft: (MeetupDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WizardQuestion(stringResource(R.string.mw_step2_q))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WizardOptionCard(draft.discipline == "BOULDER", { onDraft(draft.withDiscipline(if (draft.discipline == "BOULDER") null else "BOULDER")) }, Modifier.weight(1f)) {
                WizardCardTitle(stringResource(R.string.mw_boulder_title), stringResource(R.string.mw_boulder_sub), serif = true)
            }
            WizardOptionCard(draft.discipline == "ROUTE", { onDraft(draft.withDiscipline(if (draft.discipline == "ROUTE") null else "ROUTE")) }, Modifier.weight(1f)) {
                WizardCardTitle(stringResource(R.string.mw_route_title), stringResource(R.string.mw_route_sub), serif = true)
            }
        }

        WizardEyebrow(stringResource(R.string.mw_partners_q))
        WizardStepper(
            value = draft.partners.toString(), unit = pluralStringResource(R.plurals.mw_people, draft.partners),
            onMinus = if (draft.partners > MeetupDraft.MIN_PARTNERS) ({ onDraft(draft.withPartners(draft.partners - 1)) }) else null,
            onPlus = if (draft.partners < MeetupDraft.MAX_PARTNERS) ({ onDraft(draft.withPartners(draft.partners + 1)) }) else null
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CumbreChip(stringResource(R.string.create_meetup_rope_team), draft.isRopeTeam, { onDraft(draft.toggleRopeTeam()) })
            if (draft.isRopeTeam) Text(
                stringResource(R.string.mw_rope_team_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        WizardEyebrow(stringResource(R.string.mw_grade))
        GradeLadder(draft, onDraft)
        Text(stringResource(R.string.mw_grade_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val gearItems = MeetupGearItem.forDiscipline(draft.discipline)
        if (gearItems.isNotEmpty()) {
            WizardEyebrow(stringResource(R.string.mw_gear))
            gearItems.forEach { item -> GearRequirementRow(item, draft.gear[item], onDraft, draft) }
            Text(stringResource(R.string.mw_gear_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MeetupSummaryCard(draft)
    }
}

/** Escalera de grados deslizable: dos toques fijan el rango (de fácil a difícil). */
@Composable
private fun GradeLadder(draft: MeetupDraft, onDraft: (MeetupDraft) -> Unit) {
    val range = when {
        draft.gradeMin == null -> null
        draft.gradeMax == null || draft.gradeMax == draft.gradeMin -> draft.gradeMin!!.uppercase()
        else -> "${draft.gradeMin!!.uppercase()} – ${draft.gradeMax!!.uppercase()}"
    }
    if (range != null) Text(range, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = terraFillColor())
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MeetupDraft.GRADE_LADDER.forEach { g ->
            val on = draft.isGradeSelected(g)
            Text(
                g.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
                color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(CircleShape)
                    .background(if (on) terraFillColor() else MaterialTheme.colorScheme.surface, CircleShape)
                    .border(1.dp, if (on) terraFillColor() else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onDraft(draft.toggleGrade(g)) }.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/** Una fila de material mínimo: se pide con el chip y, si lleva cantidad (cintas, crashpads), con − y +. */
@Composable
private fun GearRequirementRow(item: String, qty: Int?, onDraft: (MeetupDraft) -> Unit, draft: MeetupDraft) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, if (qty != null) terraFillColor() else MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CumbreChip(stringResource(gearLabelRes(item)), qty != null, { onDraft(draft.toggleGear(item)) })
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (qty != null && MeetupGearItem.hasQuantity(item)) {
                StepperButton("−", if (qty > 1) ({ onDraft(draft.withGearQty(item, qty - 1)) }) else null, primary = false, description = "−", size = 36)
                Text(
                    qty.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                StepperButton("+", if (qty < MeetupGearItem.MAX_QTY) ({ onDraft(draft.withGearQty(item, qty + 1)) }) else null, primary = true, description = "+", size = 36)
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
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF1B1B1B), shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.mw_summary).uppercase(), style = EyebrowTextStyle, color = Color(0xFFBDB7A6))
        Text(line1, fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFFF5F3EE))
        Text(line2, fontSize = 13.sp, color = Color(0xFFDDD7C6))
    }
}

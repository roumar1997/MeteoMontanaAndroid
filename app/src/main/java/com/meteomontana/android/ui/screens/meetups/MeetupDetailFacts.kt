package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.ui.screens.gyms.GymActionPill
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Lo que pide la quedada: modalidad, nivel, compañeros buscados (o cordada) y el material mínimo con lo que ya
 * cubren los apuntados («✓ Cuerda 1/1 · ⚠ Cintas 4/9»), más el acceso al chat del grupo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetupFacts(meetup: Meetup, canChat: Boolean, onOpenChat: () -> Unit) {
    val grade = meetup.gradeMin?.let { min ->
        val max = meetup.gradeMax ?: min
        if (min.equals(max, true)) min.uppercase() else "${min.uppercase()}–${max.uppercase()}"
    }
    val hasFacts = meetup.discipline != null || grade != null || meetup.partnersWanted != null || meetup.gearCoverage.isNotEmpty()
    if (!hasFacts && !canChat) return
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hasFacts) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            meetup.discipline?.let { FactPill(disciplineLabel(it)) }
            grade?.let { FactPill(stringResource(R.string.meetup_facts_level, it)) }
            if (meetup.isRopeTeam) FactPill(stringResource(R.string.meetup_facts_rope_team))
            else meetup.partnersWanted?.let { FactPill(pluralStringResource(R.plurals.meetup_facts_partners, it, it)) }
        }
        if (meetup.gearCoverage.isNotEmpty()) GearCoverageBlock(meetup)
        if (canChat) GymActionPill(
            stringResource(R.string.meetup_open_chat), onOpenChat, Modifier.fillMaxWidth(), primary = true, icon = null
        )
    }
}

@Composable
private fun FactPill(text: String) {
    Text(
        text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun GearCoverageBlock(meetup: Meetup) {
    val shape = RoundedCornerShape(16.dp)
    val missing = meetup.gearMissing
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, if (missing) MaterialTheme.colorScheme.error else terraFillColor(), shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.meetup_gear_title).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val context = androidx.compose.ui.platform.LocalContext.current
        val line = meetup.gearCoverage.joinToString(" · ") { c ->
            "${if (c.ok) "✓" else "⚠"} ${context.getString(gearLabelRes(c.item))} ${c.provided}/${c.required}"
        }
        Text(line, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(if (missing) R.string.meetup_gear_missing else R.string.meetup_gear_ok), fontSize = 12.sp,
            color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

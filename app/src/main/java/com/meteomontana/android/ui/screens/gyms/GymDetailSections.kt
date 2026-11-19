package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.ui.components.PresencePrivacySheet
import com.meteomontana.android.ui.theme.CumbreStatCardShape
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.terraFillColor

@Composable
private fun SectionLabel(text: String, icon: Int? = null) {
    Row(Modifier.padding(top = Spacing.md, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp))
        Text(text.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Modalidad, horario, precio orientativo (con su fecha), servicios y fiabilidad de la ficha. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GymInfoSections(gym: Gym) {
    val body = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {

    SectionLabel(stringResource(R.string.gym_section_discipline))
    val disciplines = GymPresentation.disciplineChips(gym)
    if (disciplines.isEmpty()) Text(stringResource(R.string.gym_unknown), fontSize = 14.sp, color = muted)
    else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        disciplines.forEach { GymTag(it, gymDisciplineColor()) }
    }

    SectionLabel(stringResource(R.string.gym_section_hours), R.drawable.ic_clock)
    val hours = GymPresentation.hoursLines(gym.openingHours)
    if (hours.isEmpty()) Text(stringResource(R.string.gym_hours_unknown), fontSize = 14.sp, color = muted)
    else Column { hours.forEach { Text(it, fontSize = 14.sp, lineHeight = 22.sp, color = body) } }

    SectionLabel(stringResource(R.string.gym_section_price), R.drawable.ic_price)
    val price = GymPresentation.priceInfo(gym)
    if (price.lines.isEmpty()) Text(stringResource(R.string.gym_price_unknown), fontSize = 14.sp, color = muted)
    else Column {
        price.lines.forEach { Text(it, fontSize = 14.sp, lineHeight = 22.sp, color = body) }
        price.note?.let { Text(it, fontSize = 12.sp, color = muted, modifier = Modifier.padding(top = 2.dp)) }
    }

    SectionLabel(stringResource(R.string.gym_section_services))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ServiceRow(R.drawable.ic_autobelay, R.string.gym_service_autobelay, gym.hasAutobelay)
        ServiceRow(R.drawable.ic_gear_rental, R.string.gym_service_gear, gym.hasGearRental)
        ServiceRow(R.drawable.ic_kids, R.string.gym_service_kids, gym.kidsFriendly)
        ServiceRow(R.drawable.ic_beginner, R.string.gym_service_beginner, gym.beginnerFriendly)
    }

    Text(
        GymPresentation.verificationText(gym.verification), fontSize = 12.sp, color = muted,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Spacing.md)
    )
    if (gym.needsOsmAttribution) Text(stringResource(R.string.gym_osm), fontSize = 11.sp, color = muted)
    }
}

@Composable
private fun ServiceRow(icon: Int, label: Int, value: Boolean?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
        Text(stringResource(label), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(
            GymPresentation.serviceValue(value), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * «Quién va hoy»: quién ha marcado «Aquí» en este rocódromo y el botón para unirte (con el mismo aviso de
 * privacidad que en una escuela la primera vez que te marcas).
 */
@Composable
internal fun GymPresenceSection(
    people: List<SchoolPresence>,
    iAmHere: Boolean,
    busy: Boolean,
    error: Boolean,
    onLoad: (myUid: String?) -> Unit,
    onToggle: (myUid: String?) -> Unit
) {
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    var privacyOpen by remember { mutableStateOf(false) }
    val load by rememberUpdatedState(onLoad)
    LaunchedEffect(myUid) { load(myUid) }

    Column {
    SectionLabel(stringResource(R.string.gym_section_who))
    Row(
        Modifier.fillMaxWidth().clip(CumbreStatCardShape).background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box {
            people.take(4).forEachIndexed { i, p ->
                Box(Modifier.padding(start = (i * 18).dp).size(30.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.surface,
                    CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    p.photoUrl?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(30.dp)) }
                }
            }
        }
        Text(
            if (people.isEmpty()) stringResource(R.string.gym_here_none) else stringResource(R.string.gym_here_now, people.size),
            fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(if (iAmHere) R.string.gym_here_leave else R.string.gym_here_join),
            color = if (iAmHere) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.White,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2,
            modifier = Modifier.clip(CircleShape)
                .background(if (iAmHere) MaterialTheme.colorScheme.background else terraFillColor())
                .clickable(enabled = !busy) { if (iAmHere) onToggle(myUid) else privacyOpen = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
    if (error) Text(stringResource(R.string.gym_here_error), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
    if (privacyOpen) PresencePrivacySheet(onConfirm = { privacyOpen = false; onToggle(myUid) }, onDismiss = { privacyOpen = false })
    }
}

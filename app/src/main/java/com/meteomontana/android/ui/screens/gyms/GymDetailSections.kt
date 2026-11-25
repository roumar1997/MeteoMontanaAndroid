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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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

    GymScaleSection(gym.scale)

    SectionLabel(stringResource(R.string.gym_section_hours), R.drawable.ic_clock)
    val hours = GymPresentation.hoursLines(gym.openingHours)
    GymInfoCard(R.drawable.ic_clock, com.meteomontana.android.ui.theme.RainBlue) {
        if (hours.isEmpty()) Text(stringResource(R.string.gym_hours_unknown), fontSize = 14.sp, color = muted)
        else hours.forEach { Text(it, fontSize = 14.sp, lineHeight = 22.sp, color = body) }
    }

    SectionLabel(stringResource(R.string.gym_section_price), R.drawable.ic_price)
    val price = GymPresentation.priceInfo(gym)
    GymInfoCard(R.drawable.ic_price, com.meteomontana.android.ui.theme.Ok) {
        if (price.lines.isEmpty()) Text(stringResource(R.string.gym_price_unknown), fontSize = 14.sp, color = muted)
        else {
            price.lines.forEach { Text(it, fontSize = 14.sp, lineHeight = 22.sp, color = body) }
            price.note?.let { Text(it, fontSize = 12.sp, color = muted, modifier = Modifier.padding(top = 2.dp)) }
        }
    }

    }
}

/** Servicios (autoaseguro, alquiler, ninos, principiantes): va arriba en la ficha para verlo de un vistazo. */
@Composable
internal fun GymServicesSection(gym: Gym) {
    Column {
    SectionLabel(stringResource(R.string.gym_section_services))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ServiceRow(R.drawable.ic_autobelay, R.string.gym_service_autobelay, gym.hasAutobelay, androidx.compose.ui.graphics.Color(0xFF7B3FB0))
        ServiceRow(R.drawable.ic_gear_rental, R.string.gym_service_gear, gym.hasGearRental, com.meteomontana.android.ui.theme.RainBlue)
        ServiceRow(R.drawable.ic_kids, R.string.gym_service_kids, gym.kidsFriendly, com.meteomontana.android.ui.theme.Warn)
        ServiceRow(R.drawable.ic_beginner, R.string.gym_service_beginner, gym.beginnerFriendly, com.meteomontana.android.ui.theme.Ok)
    }
    }
}

/**
 * Escala de dificultad en una tarjeta: con colores, cada color con su rango de grado debajo (si se conoce); sin
 * colores, el degradado de grados con la paleta de grados de la app (la misma de vías y bloques).
 */
@Composable
internal fun GymScaleSection(scale: com.meteomontana.android.domain.model.gym.GymScale?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val cardShape = RoundedCornerShape(16.dp)
    Column {
        SectionLabel(stringResource(R.string.gym_section_scale))
        Column(
            Modifier.fillMaxWidth().clip(cardShape).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, cardShape).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (scale != null && scale.isColors) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    scale.levels.forEach { lv ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(parseHex(lv.hex))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)))
                            val range = GymPresentation.gradeRange(lv.gradeFrom, lv.gradeTo)
                            Text(range ?: com.meteomontana.android.util.CatalogLabels.holdColor(lv.name), fontSize = 10.sp, color = muted, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
                Text(stringResource(R.string.gym_scale_easy_hard), fontSize = 12.sp, color = muted)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("5", "6a", "6b", "6c", "7a", "7b+").forEach { g ->
                        val st = com.meteomontana.android.ui.theme.gradeChipStyle(g)
                        Box(
                            Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(9.dp)).background(st.stroke)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(g.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                color = if (st.dark) androidx.compose.ui.graphics.Color(0xFF1B1B1B) else androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
                Text(stringResource(R.string.gym_train_by_grade), fontSize = 12.sp, color = muted)
            }
        }
    }
}

/** Lo último de la ficha, pequeño y discreto: fiabilidad del dato y el crédito de OpenStreetMap (ODbL). */
@Composable
internal fun GymFooterNotes(gym: Gym) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(GymPresentation.verificationText(gym.verification), fontSize = 11.sp, color = muted)
        Text(stringResource(R.string.gym_osm_credit), fontSize = 10.sp, color = muted.copy(alpha = 0.8f))
    }
}

/** Tarjeta redondeada de un servicio: icono en cuadradito de color, nombre y «Sí» (verde), «No» (rojo) o «Sin datos» (gris) en pildora. */
@Composable
private fun ServiceRow(icon: Int, label: Int, value: Boolean?, tint: androidx.compose.ui.graphics.Color) {
    val shape = RoundedCornerShape(16.dp)
    val chip = when (value) { true -> com.meteomontana.android.ui.theme.Ok; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.onSurfaceVariant }
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GymTintedIcon(icon, tint)
        Text(stringResource(label), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(
            GymPresentation.serviceValue(value), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = chip, maxLines = 1,
            modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(chip.copy(alpha = 0.13f)).padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

/** Icono del servicio sobre un cuadradito redondeado tintado. */
@Composable
private fun GymTintedIcon(icon: Int, tint: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Tarjeta de horario o precio: icono de color a la izquierda y el texto a la derecha. */
@Composable
internal fun GymInfoCard(icon: Int, tint: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GymTintedIcon(icon, tint)
        Column(Modifier.weight(1f)) { content() }
    }
}

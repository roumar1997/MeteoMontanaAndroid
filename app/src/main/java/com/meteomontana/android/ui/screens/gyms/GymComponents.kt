package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymOpenStatus
import com.meteomontana.android.ui.theme.GymMunicipal
import com.meteomontana.android.ui.theme.GymMunicipalTextDark
import com.meteomontana.android.ui.theme.gymTone
import androidx.compose.ui.graphics.luminance

/** Cuadrado redondeado del color del tipo con su icono en blanco (fila de la lista y tarjeta del mapa). */
@Composable
internal fun GymKindBadge(kind: GymKind, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 4.3f)).background(gymTone(kind).fill),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(GymMarkerIcons.glyph(kind)), contentDescription = null, tint = Color.White,
            modifier = Modifier.size(size * 0.5f))
    }
}

/** Etiqueta pequeña (píldora de fondo suave y texto del mismo color). */
@Composable
internal fun GymTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

/** El azul de las etiquetas de modalidad (el mismo de "municipal"); se aclara en oscuro para leerse. */
@Composable
internal fun gymDisciplineColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) GymMunicipalTextDark else GymMunicipal

/**
 * Etiquetas de una ficha: tipo, cadena, modalidad, precio y estado de apertura. Se reparten en varias líneas si
 * no caben (pantalla estrecha, letra grande): nada se corta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GymTags(
    gym: Gym,
    status: GymOpenStatus,
    modifier: Modifier = Modifier,
    showKind: Boolean = true
) {
    val tone = gymTone(gym.kind)
    val ok = gymTone(GymKind.FREE).text
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (showKind) GymTag(GymPresentation.kindLabel(gym.kind), tone.text)
        if (showKind) gym.chain?.takeIf { it.isNotBlank() }?.let { GymTag(it, MaterialTheme.colorScheme.onSurfaceVariant) }
        GymPresentation.disciplineChips(gym).forEach { GymTag(it, gymDisciplineColor()) }
        GymPresentation.priceTag(gym)?.let { tag ->
            GymTag(tag, if (gym.isFree) ok else gymTone(GymKind.COMMERCIAL).text)
        }
        GymPresentation.openStatusText(status)?.let { text ->
            GymTag(text, if (status is GymOpenStatus.Open) ok else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Botón en píldora: primario relleno del color de la marca o neutro sobre papel. */
@Composable
internal fun GymActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: Int? = null,
    enabled: Boolean = true
) {
    val fg = if (primary) Color.White else MaterialTheme.colorScheme.onBackground
    Row(
        modifier
            .clip(CircleShape)
            .background(if (primary) com.meteomontana.android.ui.theme.terraFillColor() else MaterialTheme.colorScheme.surface)
            .let { if (primary) it else it.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) }
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}


package com.meteomontana.android.ui.screens.gyms

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.ui.screens.chat.openDirections
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * UNA sola tarjeta con «Estoy aquí» y «Quién va hoy» (igual que en una escuela, sin duplicar arriba y abajo):
 * «Estás aquí» + avatares + «Ya no estoy», o «Quién va hoy · N» + «Marca que estás para escribir en el chat» + «Estoy aquí».
 */
@Composable
internal fun GymPresenceCard(
    people: List<SchoolPresence>,
    iAmHere: Boolean,
    busy: Boolean,
    error: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (iAmHere) com.meteomontana.android.ui.theme.Ok else muted.copy(alpha = 0.5f)))
            Column(Modifier.weight(1f)) {
                if (iAmHere) Text(
                    stringResource(R.string.gym_here_you), fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = Serif,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    if (people.isEmpty()) stringResource(R.string.gym_who_today_none) else stringResource(R.string.gym_who_today, people.size),
                    fontWeight = if (iAmHere) FontWeight.Normal else FontWeight.Bold, fontFamily = if (iAmHere) null else Serif,
                    fontSize = if (iAmHere) 12.sp else 15.sp, color = if (iAmHere) muted else MaterialTheme.colorScheme.onBackground
                )
                if (!iAmHere) Text(stringResource(R.string.gym_who_hint), fontSize = 12.sp, color = muted, lineHeight = 15.sp)
            }
            if (people.isNotEmpty()) Box(Modifier.width((16 * (minOf(people.size, 3) - 1) + 26).dp)) {
                people.take(3).forEachIndexed { i, p ->
                    Box(
                        Modifier.padding(start = (i * 16).dp).size(26.dp).clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    ) { p.photoUrl?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(26.dp)) } }
                }
            }
            Text(
                stringResource(if (iAmHere) R.string.gym_here_leave else R.string.gym_here_top).uppercase(),
                color = if (iAmHere) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center, letterSpacing = 0.6.sp,
                modifier = Modifier.clip(CircleShape)
                    .background(if (iAmHere) MaterialTheme.colorScheme.background else terraFillColor())
                    .clickable(enabled = !busy) { if (iAmHere) onLeave() else onJoin() }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
        if (error) Text(stringResource(R.string.gym_here_error), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
    }
}

/** Fila de 4 acciones secundarias, solo icono con su nombre debajo: Cómo llegar · Llamar · Web · Compartir. */
@Composable
internal fun GymQuickActions(gym: Gym, ctx: Context, modifier: Modifier = Modifier) {
    val phone = gym.phone?.takeIf { it.isNotBlank() }
    val web = gym.website?.takeIf { it.isNotBlank() }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickAction(stringResource(R.string.gym_directions), painterRes = R.drawable.ic_directions, enabled = true, modifier = Modifier.weight(1f)) {
            openDirections(ctx, gym.lat, gym.lon, gym.name)
        }
        QuickAction(stringResource(R.string.gym_call), vector = Icons.Outlined.Call, enabled = phone != null, modifier = Modifier.weight(1f)) {
            phone?.let { dial(ctx, it) }
        }
        QuickAction(stringResource(R.string.gym_web), vector = Icons.Outlined.Language, enabled = web != null, modifier = Modifier.weight(1f)) {
            web?.let { com.meteomontana.android.util.openInApp(ctx, webUrl(it)) }
        }
        QuickAction(stringResource(R.string.gym_share), painterRes = R.drawable.ic_share_ios, enabled = true, modifier = Modifier.weight(1f)) {
            shareGym(ctx, gym)
        }
    }
}

@Composable
private fun QuickAction(
    label: String, modifier: Modifier = Modifier, vector: ImageVector? = null, painterRes: Int? = null, enabled: Boolean, onClick: () -> Unit
) {
    Column(
        modifier.clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.4f).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (vector != null) Icon(vector, null, tint = Terra, modifier = Modifier.size(21.dp))
            else if (painterRes != null) Icon(painterResource(painterRes), null, tint = Terra, modifier = Modifier.size(21.dp))
        }
        Text(label, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Abre el marcador del teléfono con el número puesto (DIAL: no llama sin que el usuario pulse). */
internal fun dial(ctx: Context, phone: String) {
    val number = phone.filter { it.isDigit() || it == '+' }
    runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
}

/** Compartir: nombre, dirección y enlace al mapa, con la hoja de compartir del sistema (como el resto de la app). */
internal fun shareGym(ctx: Context, gym: Gym) {
    val place = listOfNotNull(gym.address?.takeIf { it.isNotBlank() }, gym.city.takeIf { it.isNotBlank() }).joinToString(", ")
    val text = listOfNotNull(gym.name, place.ifEmpty { null }, "https://www.google.com/maps/search/?api=1&query=${gym.lat},${gym.lon}").joinToString("\n")
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Abre el punto del rocódromo en el mapa del móvil. */
internal fun openGymOnMap(ctx: Context, gym: Gym) {
    val uri = Uri.parse("geo:${gym.lat},${gym.lon}?q=${gym.lat},${gym.lon}(${Uri.encode(gym.name)})")
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

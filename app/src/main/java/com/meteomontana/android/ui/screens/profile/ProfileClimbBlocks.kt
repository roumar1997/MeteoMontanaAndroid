package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.HomeGym
import com.meteomontana.android.ui.screens.gyms.parseHex
import com.meteomontana.android.ui.theme.CumbreStatCardShape
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.gradeChipStyle
import com.meteomontana.android.ui.theme.terraFillColor

/** Margen izquierdo del avatar del perfil: holgura para los efectos de título (dragones, hadas) que sobresalen. */
internal val ProfileAvatarStart = 38.dp

// Piezas del perfil propio y ajeno (maqueta 4): cuatro celdas, filas finas, la tarjeta de degradado con Total / Roca /
// Rocódromo y la fila «Tu rocódromo». Todo con peso (weight) y texto que puede crecer: nada de anchos fijos.

/** Grado con el color de la paleta de grados de la app, en píldora; sin grado, una raya. */
@Composable
internal fun MaxGradePill(grade: String?, modifier: Modifier = Modifier) {
    if (grade.isNullOrBlank()) {
        Text("—", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground, modifier = modifier)
        return
    }
    val st = gradeChipStyle(grade)
    Text(
        grade.uppercase(), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
        color = if (st.dark) Color(0xFF1B1B1B) else Color.White,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(st.stroke).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun CellShell(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surface, CumbreStatCardShape).border(1.dp, MaterialTheme.colorScheme.outline, CumbreStatCardShape)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) { content() }
        Text(
            label, fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Bloques · Vías · Máx. bloque · Máx. vía, con el máximo en píldora del color de su grado. */
@Composable
internal fun ProfileCells(
    boulders: Int, routes: Int, maxBoulder: String?, maxRoute: String?,
    onBoulders: () -> Unit, onRoutes: () -> Unit, onMax: () -> Unit, modifier: Modifier = Modifier,
    schools: Int? = null, onSchools: () -> Unit = {}
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (schools != null) CellShell(stringResource(R.string.profile_schools), Modifier.weight(1f).clickable(onClick = onSchools)) { CellNumber(schools) }
        CellShell(stringResource(R.string.profile_blocks), Modifier.weight(1f).clickable(onClick = onBoulders)) { CellNumber(boulders) }
        CellShell(stringResource(R.string.profile_routes), Modifier.weight(1f).clickable(onClick = onRoutes)) { CellNumber(routes) }
        CellShell(stringResource(R.string.profile_max_boulder), Modifier.weight(1f).clickable(onClick = onMax)) { MaxGradePill(maxBoulder) }
        CellShell(stringResource(R.string.profile_max_route), Modifier.weight(1f).clickable(onClick = onMax)) { MaxGradePill(maxRoute) }
    }
}

@Composable
private fun CellNumber(n: Int) =
    Text(n.toString(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)

/** Fila fina con su cuenta y flecha («Mis publicaciones 14 ›»). */
@Composable
internal fun ProfileThinRow(label: String, count: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.background(MaterialTheme.colorScheme.surface, CumbreStatCardShape).border(1.dp, MaterialTheme.colorScheme.outline, CumbreStatCardShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
        count?.let { Text("$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp)) }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
    }
}

/**
 * Tarjeta de degradado (morado, terracota, naranja): arriba el total de días y, debajo, DOS mitades iguales, Roca y
 * Rocódromo, cada una con todos sus datos (roca: días, bloques y vías con su máximo, escuelas; rocódromo: sesiones,
 * rocódromos, ascensiones y máximo) y pulsable. La barra de abajo suma los colores y grados de lo hecho en rocódromo.
 */
@Composable
internal fun ClimbGradientCard(
    state: ProfileClimbState, rockMax: String?, onStats: () -> Unit,
    onSessions: () -> Unit, modifier: Modifier = Modifier,
    /** Mitad «Roca»: abre Estadisticas en su pestaña Roca (por defecto, [onStats]). */
    onRock: () -> Unit = onStats
) {
    val shape = RoundedCornerShape(22.dp)
    val s = state.summary
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF7B3FB0), Color(0xFFC2410C), Color(0xFFE88A3A))), shape)
            .clickable(onClick = onStats).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_climb_card_title).uppercase(), style = EyebrowTextStyle, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
                Text(
                    pluralStringResource(R.plurals.profile_days_climbing, state.totalDays, state.totalDays),
                    fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, maxLines = 1
                )
            }
            Text(
                stringResource(R.string.gym_stats_see_all).uppercase() + " ›", style = EyebrowTextStyle, color = Color(0xFF2A2924), maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHalf(
                stringResource(R.string.profile_line_rock),
                state.rockDays?.let { pluralStringResource(R.plurals.profile_days, it, it) } ?: "—",
                listOf(
                    stringResource(R.string.profile_card_blocks) to withMax(state.rockBoulders, state.rockMaxBoulder),
                    stringResource(R.string.profile_card_routes) to withMax(state.rockRoutes, state.rockMaxRoute),
                    stringResource(R.string.profile_card_schools) to state.schools.toString()
                ),
                Modifier.weight(1f).fillMaxHeight(), onRock
            )
            CardHalf(
                stringResource(R.string.profile_line_gym),
                pluralStringResource(R.plurals.profile_sessions, s.sessions, s.sessions),
                listOf(
                    stringResource(R.string.profile_card_gyms) to s.gyms.toString(),
                    stringResource(R.string.profile_card_ascents) to s.ascents.toString(),
                    stringResource(R.string.profile_card_max) to (s.maxGrade?.uppercase() ?: "—")
                ),
                Modifier.weight(1f).fillMaxHeight(), onSessions
            )
        }
        if (state.segments.isNotEmpty()) Row(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)) {
            state.segments.forEach { seg ->
                Box(Modifier.weight(seg.count.toFloat()).height(7.dp).background(seg.hex?.let { parseHex(it) } ?: gradeChipStyle(seg.grade).stroke))
            }
        }
    }
}

private fun withMax(count: Int, max: String?) = if (max.isNullOrBlank()) count.toString() else "$count · ${max.uppercase()}"

/** Una mitad de la tarjeta (Roca o Rocódromo): título, cifra grande y tres líneas «etiqueta … valor». */
@Composable
private fun CardHalf(title: String, big: String, lines: List<Pair<String, String>>, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = EyebrowTextStyle, color = Color.White.copy(alpha = 0.9f), maxLines = 1, modifier = Modifier.weight(1f))
            Text("›", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        }
        Text(big, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, maxLines = 1)
        lines.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * «Tu rocódromo» / «Su rocódromo» y «Semanas seguidas»; el chip del rocódromo abre su ficha. En el perfil propio, sin
 * rocódromo elegido sale un hueco «Elige tu rocódromo» ([onPick]); en el de otra persona, sin rocódromo no sale nada.
 */
@Composable
internal fun HomeGymAndStreak(
    home: HomeGym?, streakWeeks: Int, onOpenGym: (Long) -> Unit, modifier: Modifier = Modifier,
    own: Boolean = true, onPick: (() -> Unit)? = null,
    /** Lo ultimo de la fila (p. ej. el boton pequeño «+ Entreno»). */
    trailing: (@Composable () -> Unit)? = null
) {
    val canPick = own && onPick != null && home == null
    if (home == null && !canPick && streakWeeks <= 0 && trailing == null) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canPick) Row(
            Modifier.weight(1f).clip(CumbreStatCardShape).border(1.dp, MaterialTheme.colorScheme.outline, CumbreStatCardShape)
                .clickable { onPick?.invoke() }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Text("+", color = terraFillColor(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text(stringResource(R.string.home_gym_pick), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        if (home != null) Row(
            Modifier.weight(1f).clip(CumbreStatCardShape).background(MaterialTheme.colorScheme.surface, CumbreStatCardShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CumbreStatCardShape).clickable { onOpenGym(home.id) }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(terraFillColor()), contentAlignment = Alignment.Center) {
                Text(home.name.take(1).uppercase(), color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (own) R.string.home_gym_yours else R.string.home_gym_theirs).uppercase(), fontFamily = Mono, fontSize = 8.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(home.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        if (streakWeeks > 0) Row(
            Modifier.weight(1f).clip(CumbreStatCardShape).background(MaterialTheme.colorScheme.surface, CumbreStatCardShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CumbreStatCardShape).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.LocalFireDepartment, null, tint = terraFillColor(), modifier = Modifier.size(24.dp))
            Column {
                Text(stringResource(R.string.gym_stats_streak).uppercase(), fontFamily = Mono, fontSize = 8.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(streakWeeks.toString(), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        trailing?.invoke()
    }
}

/** Cabecera compacta: avatar a la izquierda (lo pinta el host sin recortar) y, a su lado, nombre, @usuario y seguidos · seguidores. */
@Composable
internal fun CompactProfileHeader(
    name: String, handle: String, uid: String, followers: Long, following: Long,
    onFollowers: () -> Unit, onFollowing: () -> Unit, avatarSlot: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // Hueco del avatar: empieza en ProfileAvatarStart (más a la derecha que el margen, para que el efecto del título no se corte).
        Spacer(Modifier.width(avatarSlot + ProfileAvatarStart - 16.dp + 8.dp).height(avatarSlot + 8.dp))
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(name, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 23.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f, fill = false))
                com.meteomontana.android.ui.components.VerifiedCheck(uid, 18.dp)
            }
            Text(handle, fontFamily = Mono, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 2.dp)) {
                FollowInline(following, stringResource(R.string.profile_following).lowercase(), onFollowing)
                FollowInline(followers, stringResource(R.string.profile_followers).lowercase(), onFollowers)
            }
        }
        trailing()
    }
}

@Composable
private fun FollowInline(value: Long, label: String, onClick: () -> Unit) {
    Row(Modifier.clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$value", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

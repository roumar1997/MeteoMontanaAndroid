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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.ui.components.IconAction
import com.meteomontana.android.ui.components.IconActionRow
import com.meteomontana.android.ui.components.LocalTabBarInset
import com.meteomontana.android.ui.screens.chat.openDirections
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.domain.util.Geo as GeoUtil

/**
 * Ficha completa de un rocódromo (maqueta aprobada): etiquetas, nombre, dirección y distancia, acciones
 * (Cómo llegar, Web, Instagram), modalidad, horario, precio con su fecha, servicios, «Quién va hoy», crear quedada
 * y «¿Algo no es así? Corregir». Lo que no se sabe sale como «Sin confirmar», nunca como «No».
 */
@Composable
fun GymDetailScreen(
    onBack: () -> Unit,
    onCreateMeetup: (Gym) -> Unit,
    onOpenChat: (Gym) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GymDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gym = state.gym
    val ctx = LocalContext.current
    var reportOpen by remember { mutableStateOf(false) }
    var trainOpen by remember { mutableStateOf(false) }
    val userLoc = com.meteomontana.android.ui.components.rememberUserLocation()
    var privacyOpen by remember { mutableStateOf(false) }
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    androidx.compose.runtime.LaunchedEffect(myUid) { viewModel.loadPresence(myUid) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        IconActionRow(
            count = 4,
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) { item ->
            IconAction(description = stringResource(R.string.common_close), onClick = onBack, modifier = item,
                icon = Icons.AutoMirrored.Outlined.ArrowBack)
            IconAction(description = stringResource(R.string.gym_chat_open), onClick = { gym?.let(onOpenChat) }, modifier = item,
                icon = Icons.Outlined.Forum)
            IconAction(description = stringResource(R.string.gym_map_open), onClick = { gym?.let { openGymOnMap(ctx, it) } }, modifier = item,
                icon = Icons.Outlined.Place, enabled = gym != null)
            IconAction(
                description = stringResource(if (state.favorite) R.string.gym_favorite_remove else R.string.gym_favorite_add),
                onClick = viewModel::toggleFavorite, modifier = item, active = state.favorite,
                icon = if (state.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        when {
            gym == null && state.loadFailed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.gym_load_error), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(Spacing.xl))
            }
            gym == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg).padding(top = Spacing.md, bottom = LocalTabBarInset.current + Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                GymHeader(gym, userLoc?.let { GeoUtil.haversineKm(it.lat, it.lon, gym.lat, gym.lon) })
                GymPresenceCard(
                    people = state.people, iAmHere = state.iAmHere, busy = state.presenceBusy, error = state.presenceError,
                    onJoin = { privacyOpen = true }, onLeave = { viewModel.togglePresence(myUid) }
                )
                GymQuickActions(gym, ctx, Modifier.padding(top = Spacing.xs))
                gym.instagram?.takeIf { it.isNotBlank() }?.let { insta ->
                    Text(
                        stringResource(R.string.gym_instagram), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clickable { openUrl(ctx, instagramUrl(insta)) }.padding(Spacing.xs)
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GymActionPill(
                        stringResource(if (state.trainedToday) R.string.gym_train_short_done else R.string.gym_train_short),
                        { trainOpen = true }, Modifier.weight(1f), primary = !state.trainedToday
                    )
                    GymActionPill(stringResource(R.string.gym_meetup_short), { onCreateMeetup(gym) }, Modifier.weight(1f))
                }
                if (state.trainedToday) Text(
                    stringResource(R.string.gym_train_undo), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = viewModel::toggleTraining).padding(Spacing.sm)
                )
                GymServicesSection(gym)
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { GymPhotoContribute(gym) }
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(CircleShape)
                            .border(androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant), CircleShape)
                            .clickable { reportOpen = true }.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.gym_fix_cta), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
                GymInfoSections(gym)
                GymFooterNotes(gym)
            }
        }
    }
    if (privacyOpen) com.meteomontana.android.ui.components.PresencePrivacySheet(
        onConfirm = { privacyOpen = false; viewModel.togglePresence(myUid) }, onDismiss = { privacyOpen = false }
    )
    if (reportOpen && gym != null) GymReportSheet(gym = gym, onDismiss = { reportOpen = false })
    if (trainOpen && gym != null) {
        val day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", java.util.Locale.getDefault()))
        GymTrainSheet(gym, stringResource(R.string.gym_train_today_label, day), onDismiss = { trainOpen = false; viewModel.refreshTrained() })
    }

}

@Composable
private fun GymHeader(gym: Gym, distanceKm: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GymTags(gym, gym.openStatusNow())
        Text(
            gym.name, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 27.sp,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = Spacing.xs)
        )
        val line = listOfNotNull(
            gym.address?.takeIf { it.isNotBlank() } ?: listOf(gym.city,
                gym.province).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null },
            GymPresentation.distanceText(distanceKm)
        ).joinToString(" · ")
        if (line.isNotEmpty()) Text(line, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun openUrl(ctx: Context, url: String) {
    com.meteomontana.android.util.openInApp(ctx, url)
}

internal fun webUrl(raw: String): String = com.meteomontana.android.domain.model.gym.GymLinks.webUrl(raw)

/** Acepta «@nombre», «nombre» o la URL entera. */
internal fun instagramUrl(raw: String): String = com.meteomontana.android.domain.model.gym.GymLinks.instagramUrl(raw)

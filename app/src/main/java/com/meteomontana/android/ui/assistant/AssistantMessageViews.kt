package com.meteomontana.android.ui.assistant

import com.meteomontana.android.presentation.assistant.AssistantLabels
import com.meteomontana.android.presentation.assistant.AssistantPresenter
import com.meteomontana.android.presentation.assistant.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.ui.assistant.photo.AssistantPhotoMatchesView
import com.meteomontana.android.ui.assistant.photo.StonePhotoWords
import com.meteomontana.android.ui.assistant.photo.rememberStonePhotoSource
import com.meteomontana.android.domain.model.AssistantOption
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraBg
import com.meteomontana.android.ui.theme.inkButtonColor
import com.meteomontana.android.ui.theme.terraFillColor
import com.meteomontana.android.util.AppLanguage

// Un mensaje del asistente: burbuja, seguimientos, chips, botones de confirmar y opciones.

@Composable
internal fun MessageView(
    vm: AssistantViewModel, m: AssistantMessage, loading: Boolean,
    presenter: AssistantPresenter, aw: AssistantWords, words: ChatWords, showFollowUps: Boolean
) {
    if (m.isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(m.text, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.padding(start = 44.dp).clip(UserBubbleShape).background(terraFillColor()).padding(horizontal = 14.dp, vertical = 10.dp))
        }
        return
    }
    val lang = AppLanguage.effective(LocalContext.current)
    BotRow {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.text, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
                modifier = Modifier.weight(1f, fill = false).shadow(2.dp, BotBubbleShape).clip(BotBubbleShape)
                    .background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 10.dp))
            SpeakerButton(vm, m, lang, words)
        }
        if (m.photoMatches.isNotEmpty()) AssistantPhotoMatchesView(m.photoMatches, StonePhotoWords(lang)) { c -> vm.requestOpen(c.schoolId, c.lineId) }
        if (m.chips.isNotEmpty()) ChipsRow(m.chips, presenter)
        m.restrictions?.let { AssistantRestrictionsView(it, presenter) { id -> vm.requestOpen(id) } }
        if (m.trip.isNotEmpty()) AssistantTripView(m.trip, presenter) { id -> vm.requestOpen(id) }
        m.recommendation?.let { AssistantRecommendationView(it, presenter, aw) { id -> vm.requestOpen(id) } }
        m.breakdown?.let { AssistantBreakdownView(it, presenter, aw) { school, via -> vm.requestOpen(school, via) } }
        m.weather?.let { AssistantWeatherCard(it, presenter, aw) }
        if (m.journalEntries.isNotEmpty()) AssistantJournalListView(m.journalEntries, m.journalTotal, presenter) { school, via -> vm.requestOpen(school, via) }
        m.retryText?.let { RetryButton(words, enabled = !loading) { vm.retry(it) } }
        if (m.pendingActions.any { it.state == PendingAction.State.WAITING }) ConfirmButtons(vm, m, presenter, enabled = !loading)
        if (m.hits.isNotEmpty()) AssistantHitsView(m.hits, presenter, aw) { school, via -> vm.requestOpen(school, via) }
        if (m.restored && m.hasCards) presenter.savedNote(m.createdAt)?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (m.options.isNotEmpty()) OptionsColumn(vm, m.options)
        if (showFollowUps) FollowUpsRow(vm, presenter.followUps(m))
    }
}

/** Ideas para seguir preguntando debajo de la última respuesta: se envían tal cual o abren la escuela. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FollowUpsRow(vm: AssistantViewModel, items: List<AssistantPresenter.FollowUp>) {
    if (items.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            Text(item.title, color = Terra, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.5.dp, Terra, RoundedCornerShape(50))
                    .clickable { item.openSchoolId?.let { vm.requestOpen(it) } ?: vm.send(item.title) }
                    .padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

/** Lo que ha entendido, en pastillas con un icono pequeño (sitio, sol/sombra, cuándo…). */
@Composable
internal fun ChipsRow(chips: List<String>, presenter: AssistantPresenter) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { chip ->
            Row(
                Modifier.clip(CircleShape).background(TerraBg).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presenter.chipIcon(chip)?.let { Icon(chipVector(it), null, tint = Terra, modifier = Modifier.size(11.dp)) }
                Text(chip, color = Terra, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

internal fun chipVector(icon: AssistantPresenter.ChipIcon) = when (icon) {
    AssistantPresenter.ChipIcon.SHADE -> Icons.Outlined.Contrast
    AssistantPresenter.ChipIcon.SUN -> Icons.Outlined.WbSunny
    AssistantPresenter.ChipIcon.MORNING -> Icons.Outlined.WbTwilight
    AssistantPresenter.ChipIcon.AFTERNOON -> Icons.Outlined.WbTwilight
    AssistantPresenter.ChipIcon.BOULDER -> Icons.Outlined.ViewInAr
    AssistantPresenter.ChipIcon.ROUTE -> Icons.Outlined.VerticalAlignTop
    AssistantPresenter.ChipIcon.LOCATION -> Icons.Outlined.NearMe
    AssistantPresenter.ChipIcon.STAR -> Icons.Outlined.StarOutline
    AssistantPresenter.ChipIcon.NO_RAIN -> Icons.Outlined.WbCloudy
    AssistantPresenter.ChipIcon.TOPO -> Icons.Outlined.Image
    AssistantPresenter.ChipIcon.NOT_DONE -> Icons.Outlined.RadioButtonUnchecked
    AssistantPresenter.ChipIcon.MOST_LINES -> Icons.Outlined.FormatListNumbered
    AssistantPresenter.ChipIcon.BETA -> Icons.Outlined.Accessibility
    AssistantPresenter.ChipIcon.FACE -> Icons.Outlined.Explore
    AssistantPresenter.ChipIcon.CALENDAR -> Icons.Outlined.CalendarMonth
    AssistantPresenter.ChipIcon.PIN -> Icons.Outlined.Place
    AssistantPresenter.ChipIcon.ROCK -> Icons.Outlined.Terrain
}

/** Botón de píldora terracota con icono (Reintentar, Confirmar, Apuntar las N), como en iOS. */
@Composable
internal fun PrimaryPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(terraFillColor().copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Tras un fallo pasajero, repetir la misma pregunta con un toque (sin escribirla de nuevo). */
@Composable
internal fun RetryButton(words: ChatWords, enabled: Boolean, onClick: () -> Unit) =
    PrimaryPill(words.retry.lowercase().replaceFirstChar { it.uppercase() }, Icons.Outlined.Refresh, enabled, onClick)

@Composable
internal fun CancelPill(presenter: AssistantPresenter, enabled: Boolean, onClick: () -> Unit) {
    Text(presenter.cancelWord(), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = Modifier.clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp))
}

/** Confirmar / Cancelar para una acción propuesta: la app nunca hace nada por su cuenta. */
@Composable
internal fun ConfirmButtons(vm: AssistantViewModel, m: AssistantMessage, presenter: AssistantPresenter, enabled: Boolean) {
    val waiting = m.pendingActions.filter { it.state == PendingAction.State.WAITING }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            m.confirmAll -> {
                // Varias vías del día: se enseñan todas y se apuntan con un solo toque.
                waiting.forEach { action ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(if (action.kind == "LOG_PROJECT") Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle,
                            null, tint = Terra, modifier = Modifier.size(18.dp))
                        Text(logLine(action, presenter), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryPill(presenter.logAll(waiting.size), Icons.Outlined.Check, enabled) { vm.confirmAll(m.id) }
                    CancelPill(presenter, enabled) { vm.cancel(m.id) }
                }
            }
            waiting.size > 1 -> {
                // Varias candidatas: una tarjeta por cada una para elegir, y Cancelar debajo.
                waiting.forEach { action -> OptionCard(confirmTitle(action, presenter), enabled) { vm.confirm(m.id, action.id) } }
                CancelPill(presenter, enabled) { vm.cancel(m.id) }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                waiting.forEach { action -> PrimaryPill(presenter.confirmWord(), Icons.Outlined.Check, enabled) { vm.confirm(m.id, action.id) } }
                CancelPill(presenter, enabled) { vm.cancel(m.id) }
            }
        }
    }
}

/** "Zarzamora · 7a · Albarracín — hecha" / "— proyecto". */
internal fun logLine(action: PendingAction, p: AssistantPresenter): String {
    val hit = action.hit ?: return action.schoolName
    val grade = hit.grade?.let { " · $it" } ?: ""
    val what = if (action.kind == "LOG_PROJECT") p.projectWord() else p.doneWord()
    return "${hit.lineName ?: hit.blockName}$grade · ${hit.schoolName} — $what"
}

/** El nombre de cada candidata para poder elegir. */
internal fun confirmTitle(action: PendingAction, p: AssistantPresenter): String {
    val hit = action.hit ?: return p.confirmWord()
    val grade = hit.grade?.let { " · $it" } ?: ""
    return "${hit.lineName ?: hit.blockName}$grade · ${hit.schoolName}"
}

/** Tarjeta redondeada tocable con flecha: opciones de "¿a cuál te refieres?" y candidatas a confirmar. */
@Composable
internal fun OptionCard(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().shadow(2.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun OptionsColumn(vm: AssistantViewModel, options: List<AssistantOption>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option -> OptionCard(option.name) { vm.send(option.name) } }
    }
}

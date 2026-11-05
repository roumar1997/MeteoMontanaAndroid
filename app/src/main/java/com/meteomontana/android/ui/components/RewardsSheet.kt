package com.meteomontana.android.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandCircleDown
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.UserRewards
import com.meteomontana.android.domain.repository.AchievementRarity
import com.meteomontana.android.domain.usecase.journal.AchievementsCalculator
import com.meteomontana.android.domain.usecase.journal.GetAchievementRarityUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyAchievementIdsUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyRewardsUseCase
import com.meteomontana.android.domain.usecase.profile.GetUserRewardsUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateMyRewardsUseCase
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.terraFillColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Hoja del título (nivel, viero/bloquero, logro destacado y títulos), aparte de RewardsBlock.kt.

/** Elegir el título (entre los desbloqueados) y si eres viero, bloquero o las dos. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RewardsSheet(rewards: UserRewards, mineIds: Set<String>?, rarity: AchievementRarity?, onDismiss: () -> Unit,
                         onHide: (Boolean) -> Unit, onSave: (String?, Int?, String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Se desplaza: con todos los títulos y los logros no cabe en una pantalla (Álvaro, 2026-10-19).
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LevelProgress(rewards)
            Text(stringResource(R.string.climber_prompt_title), style = EyebrowTextStyle, color = Terra)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (UserRewards.CLIMBER_TYPES + "").chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { t ->
                            val on = (rewards.climberType ?: "") == t
                            Box(Modifier.weight(1f)) { ClimberChip(t.ifEmpty { null }, on) { onSave(t, null, null) } }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f)) {}
                    }
                }
            }
            // Logro destacado: desplegable; uno de los desbloqueados, con chips por dificultad como en las estadísticas.
            var featuredOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            var tierFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Int?>(null) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Terra.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { featuredOpen = !featuredOpen }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.featured_pick), style = EyebrowTextStyle, color = Terra, modifier = Modifier.weight(1f))
                Text(rewards.featuredAchievement?.let { AchievementsCalculator.byId(it) }
                    ?.let { com.meteomontana.android.ui.screens.profile.achievementText(it) } ?: stringResource(R.string.featured_none),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp))
                Text(if (featuredOpen) "▴" else "▾", color = Terra)
            }
            val allUnlocked = mineIds?.let { ids -> AchievementsCalculator.catalog.filter { it.id in ids }.sortedByDescending { it.tier.ordinal } }
            val unlocked = allUnlocked?.filter { tierFilter == null || it.tier.ordinal == tierFilter }
            when {
                !featuredOpen -> Unit
                mineIds == null -> Text(stringResource(R.string.featured_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                allUnlocked.isNullOrEmpty() -> Text(stringResource(R.string.featured_none_synced), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TierPick(stringResource(R.string.ach_filter_all), Terra, tierFilter == null, allUnlocked.size) { tierFilter = null }
                        allUnlocked.map { it.tier }.distinct().sortedBy { it.ordinal }.forEach { t ->
                            TierPick(stringResource(tierNameRes(t)).uppercase(), com.meteomontana.android.ui.screens.profile.tierColor(t),
                                tierFilter == t.ordinal, allUnlocked.count { it.tier == t }) { tierFilter = if (tierFilter == t.ordinal) null else t.ordinal }
                        }
                    }
                    val noneOn = rewards.featuredAchievement == null
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (noneOn) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .clickable { onSave(null, null, "") }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.featured_none), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (noneOn) Icon(Icons.Filled.CheckCircle, null, tint = Terra)
                    }
                    (unlocked ?: emptyList()).forEach { a ->
                        AchievementPickRow(a, rarity, rewards.featuredAchievement == a.id) { onSave(null, null, a.id) }
                    }
                }
            }
            Text(stringResource(R.string.rewards_title_pick), style = EyebrowTextStyle, color = Terra)
            rewards.specialTitle?.takeIf { it.isNotBlank() }?.let { special ->
                SpecialTitle(special, rewards.specialColor)
                Text(stringResource(R.string.rewards_special_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            // «Sin título»: no enseñar ninguno (Álvaro, 2026-10-19).
            val hidden = rewards.titleHidden
            Row(
                Modifier.clip(CircleShape)
                    .background(if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.surface)
                    .border(if (hidden) 3.dp else 1.dp, if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onHide(!hidden) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(if (hidden) Icons.Filled.CheckCircle else Icons.Filled.VisibilityOff, null,
                    tint = if (hidden) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Text(stringResource(R.string.rewards_no_title), fontSize = 14.sp,
                    fontWeight = if (hidden) FontWeight.Bold else FontWeight.Medium,
                    color = if (hidden) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.rewards_title_hint, rewards.approved), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ALL_LEVELS.forEach { l ->
                    val has = l in rewards.unlockedLevels
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TitlePill(l, on = !rewards.titleHidden && rewards.titleLevel == l, locked = !has, showCheck = true,
                            modifier = if (has) Modifier.clickable { onSave(null, l, null) } else Modifier)
                        if (!has) Text(stringResource(R.string.rewards_needs, l), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** Qué es su título y cómo se consigue (espejo de titleInfo de iOS). */
@Composable
internal fun titleInfoText(r: com.meteomontana.android.domain.model.UserRewards): String {
    val special = r.specialTitle
    if (!special.isNullOrBlank()) {
        val base = stringResource(R.string.title_info_special, special)
        return when (TitleEffectKind.from(r.titleEffect)) {
            TitleEffectKind.FAIRIES -> base + " " + stringResource(R.string.title_info_fairies)
            TitleEffectKind.DRAGONS -> base + " " + stringResource(R.string.title_info_dragons)
            null -> base
        }
    }
    if (r.titleLevel <= 0) return stringResource(R.string.title_info_starter, rewardTitle(0))
    return stringResource(R.string.title_info_level, rewardTitle(r.titleLevel), r.titleLevel, r.approved)
}

/** «N aportaciones aprobadas · siguiente nivel con M» y su barra. Vive en la hoja del título, no en el perfil. */
@Composable
internal fun LevelProgress(rewards: UserRewards) {
    val next = rewards.nextLevel ?: return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.rewards_progress, rewards.approved, next),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
        val from = rewards.level
        LinearProgressIndicator(
            progress = { ((rewards.approved - from).toFloat() / (next - from)).coerceIn(0f, 1f) },
            modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = terraFillColor(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}

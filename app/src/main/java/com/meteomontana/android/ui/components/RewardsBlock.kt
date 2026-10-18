package com.meteomontana.android.ui.components

import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.draw.clip
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

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val getUserRewards: GetUserRewardsUseCase,
    private val getMyRewards: GetMyRewardsUseCase,
    private val updateMyRewards: UpdateMyRewardsUseCase,
    private val getAchievementRarity: GetAchievementRarityUseCase,
    private val getMyAchievementIds: GetMyAchievementIdsUseCase,
) : ViewModel() {
    private val _rewards = MutableStateFlow<UserRewards?>(null)
    val rewards: StateFlow<UserRewards?> = _rewards.asStateFlow()

    /** Cuánta gente tiene cada logro (null = sin red o aún no se sabe: se enseña solo la dificultad). */
    private val _rarity = MutableStateFlow<AchievementRarity?>(null)
    val rarity: StateFlow<AchievementRarity?> = _rarity.asStateFlow()

    /** Mis logros ya sincronizados, para elegir el destacado (null = aún no cargados). */
    private val _mineIds = MutableStateFlow<Set<String>?>(null)
    val mineIds: StateFlow<Set<String>?> = _mineIds.asStateFlow()

    /** [uid] null = las mías. Sin red, no se muestra nada. */
    fun load(uid: String?) {
        viewModelScope.launch {
            _rewards.value = runCatching { if (uid == null) getMyRewards() else getUserRewards(uid) }.getOrNull()
        }
        viewModelScope.launch { _rarity.value = runCatching { getAchievementRarity() }.getOrNull() }
    }

    fun loadMineIds() {
        viewModelScope.launch { _mineIds.value = runCatching { getMyAchievementIds() }.getOrNull() }
    }

    fun update(climberType: String?, titleLevel: Int?, featured: String? = null) {
        viewModelScope.launch {
            runCatching { updateMyRewards(climberType, titleLevel, featured) }.onSuccess { _rewards.value = it }
        }
    }
}

/** Texto del título de cada nivel (0, 1, 5 … 50). */
@Composable
fun rewardTitle(level: Int): String = stringResource(when (level) {
    0 -> R.string.title_l0; 1 -> R.string.title_l1; 5 -> R.string.title_l5; 10 -> R.string.title_l10
    15 -> R.string.title_l15; 20 -> R.string.title_l20; 25 -> R.string.title_l25; 30 -> R.string.title_l30
    35 -> R.string.title_l35; 40 -> R.string.title_l40; 45 -> R.string.title_l45; else -> R.string.title_l50
})

/** Cada título tiene su color y su icono (Álvaro, 2026-10-18: «que cada título tenga su propio estilo»). */
private fun rewardStyle(level: Int): Pair<Color, ImageVector> = when (level) {
    0 -> Color(0xFF73787F) to Icons.Filled.Eco
    1 -> Color(0xFFCC5C33) to Icons.Filled.BackHand
    5 -> Color(0xFF338C4D) to Icons.Filled.Hiking
    10 -> Color(0xFF2973BF) to Icons.Filled.Map
    15 -> Color(0xFF0D8C8C) to Icons.Filled.GpsFixed
    20 -> Color(0xFF5C599E) to Icons.Filled.Shield
    25 -> Color(0xFFD97A1A) to Icons.Filled.Gesture
    30 -> Color(0xFFB82E38) to Icons.Filled.Landscape
    35 -> Color(0xFF454FB3) to Icons.Filled.Timeline
    40 -> Color(0xFF8C409E) to Icons.Filled.MenuBook
    45 -> Color(0xFF4D738C) to Icons.Filled.Air
    else -> Color(0xFFB8860B) to Icons.Filled.WorkspacePremium
}

/** Píldora de un título con su color e icono; apagada con candado si aún no se ha desbloqueado. */
@Composable
private fun TitlePill(level: Int, on: Boolean = false, locked: Boolean = false, modifier: Modifier = Modifier) {
    val (color, icon) = rewardStyle(level)
    val fg = if (on) Color.White else if (locked) MaterialTheme.colorScheme.onSurfaceVariant else color
    Row(
        modifier.clip(CircleShape)
            .background(if (on) color else color.copy(alpha = if (locked) 0.04f else 0.10f))
            .border(1.dp, color.copy(alpha = if (locked) 0.25f else 0.7f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(if (locked) Icons.Filled.Lock else icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(rewardTitle(level), color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Oro del nivel máximo: un degradado que se desplaza (brillo) sobre el texto y el borde. */
private val GoldColors = listOf(Color(0xFFB8860B), Color(0xFFFFD54A), Color(0xFFFFF6C2), Color(0xFFFFD54A), Color(0xFFB8860B))

/**
 * El título. El nivel 50 («Señor de la Cumbre») se nota a la legua: oro con brillo que recorre las letras, destellos
 * a los lados y un borde dorado. El resto, en terracota como siempre (Álvaro, 2026-10-18).
 */
@Composable
private fun RewardTitle(level: Int) {
    val title = rewardTitle(level)
    if (level < 50) {
        TitlePill(level)
        return
    }
    val shine by androidx.compose.animation.core.rememberInfiniteTransition(label = "oro").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2600, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse),
        label = "brillo")
    val gold = androidx.compose.ui.graphics.Brush.linearGradient(
        GoldColors,
        start = androidx.compose.ui.geometry.Offset(-300f + 700f * shine, 0f),
        end = androidx.compose.ui.geometry.Offset(100f + 700f * shine, 90f))
    Row(
        Modifier.clip(CircleShape).border(androidx.compose.foundation.BorderStroke(1.5.dp, gold), CircleShape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("✦", style = androidx.compose.ui.text.TextStyle(brush = gold, fontSize = 14.sp))
        Text(title, textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(
                brush = gold, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                shadow = androidx.compose.ui.graphics.Shadow(Color(0x88FFB300), blurRadius = 16f)))
        Text("✦", style = androidx.compose.ui.text.TextStyle(brush = gold, fontSize = 14.sp))
    }
}

@Composable
private fun climberTypeLabel(type: String?): String = stringResource(when (type) {
    "ROUTES" -> R.string.climber_routes; "BOULDER" -> R.string.climber_boulder; "BOTH" -> R.string.climber_both
    else -> R.string.climber_none
})

private fun climberTypeStyle(type: String?): Pair<Color, ImageVector> = when (type) {
    "ROUTES" -> Color(0xFF2973BF) to Icons.Filled.Timeline
    "BOULDER" -> Color(0xFFD97A1A) to Icons.Filled.ViewInAr
    "BOTH" -> Color(0xFF338C4D) to Icons.Filled.SwapVert
    else -> Color(0xFF73787F) to Icons.Filled.HelpOutline
}

@Composable
private fun ClimberChip(type: String?, on: Boolean, onClick: () -> Unit) {
    val (color, icon) = climberTypeStyle(type)
    val fg = if (on) Color.White else color
    Row(
        Modifier.clip(CircleShape).background(if (on) color else color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.7f), CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(climberTypeLabel(type), color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

private const val PROMPT_KEY = "climber_type_prompt_done"

/**
 * Bajo el nombre del perfil: título, insignias y nivel. En el tuyo ([uid] null) además: tocar abre la hoja para elegir
 * título y si eres viero o bloquero, y la primera vez sale una tarjeta que lo pregunta (se puede cerrar).
 * Las ilustraciones de Cumbri por nivel llegarán aparte (PLAN_MEJORAS §F).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RewardsBlock(uid: String?, viewModel: RewardsViewModel = hiltViewModel(key = "rewards_${uid ?: "me"}")) {
    LaunchedEffect(uid) { viewModel.load(uid) }
    val r by viewModel.rewards.collectAsStateWithLifecycle()
    val rarity by viewModel.rarity.collectAsStateWithLifecycle()
    val mineIds by viewModel.mineIds.collectAsStateWithLifecycle()
    val rewards = r ?: return
    val own = uid == null
    var sheet by remember { mutableStateOf(false) }
    var badgeInfo by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(sheet) { if (sheet) viewModel.loadMineIds() }
    badgeInfo?.let { res ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { badgeInfo = null },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { badgeInfo = null }) { Text("OK") } },
            text = { Text(stringResource(res)) })
    }

    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().then(if (own) Modifier.clickable { sheet = true } else Modifier)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RewardTitle(rewards.titleLevel)
        // El logro que la persona ha elegido enseñar (con su dificultad y el % de gente que lo tiene).
        FeaturedAchievement(rewards.featuredAchievement, rarity)
        if (rewards.badges.isNotEmpty() || rewards.climberType != null || own) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rewards.climberType?.let { Badge(climberTypeLabel(it), climberTypeStyle(it).first) }
                    ?: if (own) Badge(stringResource(R.string.rewards_pick_type), MaterialTheme.colorScheme.onSurfaceVariant) else Unit
                if ("COLLABORATOR" in rewards.badges) Badge(stringResource(R.string.badge_collaborator), Terra) { badgeInfo = R.string.badge_collaborator_info }
                if ("TRUSTED" in rewards.badges) Badge(stringResource(R.string.badge_trusted), Ok) { badgeInfo = R.string.badge_trusted_info }
            }
        }
        if (own) {
            Text("✎ " + stringResource(R.string.rewards_tap_hint), color = Terra.copy(alpha = 0.85f), fontFamily = Mono,
                fontWeight = FontWeight.Bold, fontSize = 8.5.sp, textAlign = TextAlign.Center)
        }
        if (own) {
            rewards.nextLevel?.let { next ->
                Text(stringResource(R.string.rewards_progress, rewards.approved, next),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                val from = rewards.level
                LinearProgressIndicator(
                    progress = { ((rewards.approved - from).toFloat() / (next - from)).coerceIn(0f, 1f) },
                    modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = terraFillColor(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
    if (own) ClimberTypePrompt(rewards) { viewModel.update(it, null) }
    if (sheet) RewardsSheet(rewards, mineIds, rarity, onDismiss = { sheet = false }) { type, title, featured -> viewModel.update(type, title, featured) }
}

@Composable
private fun Badge(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Text(text.uppercase(), color = color, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        modifier = Modifier.clip(CircleShape).border(1.dp, color, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 4.dp))
}

/** Con menos escaladores sincronizados que esto el porcentaje no dice nada: se enseña solo la dificultad. */
private const val MIN_USERS_FOR_PERCENT = 5

/** «★ 3 bloques de 7c o más en un día» con «ÉPICO · 3% de escaladores» debajo, en el color de su dificultad. */
@Composable
private fun FeaturedAchievement(id: String?, rarity: AchievementRarity?) {
    val a = id?.let { AchievementsCalculator.byId(it) } ?: return
    val color = com.meteomontana.android.ui.screens.profile.tierColor(a.tier)
    val enough = rarity != null && rarity.totalUsers >= MIN_USERS_FOR_PERCENT
    val pct = if (enough) rarity?.percentById?.get(a.id) ?: 0 else 0
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("★ " + com.meteomontana.android.ui.screens.profile.achievementText(a), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(
            stringResource(tierNameRes(a.tier)).uppercase() + if (enough) " · " + (if (pct < 1) stringResource(R.string.ach_pct_lt1) else stringResource(R.string.ach_pct, pct)) else "",
            fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = color)
    }
}

private fun tierNameRes(t: com.meteomontana.android.domain.usecase.journal.AchievementTier) = when (t) {
    com.meteomontana.android.domain.usecase.journal.AchievementTier.COMMON -> R.string.tier_common
    com.meteomontana.android.domain.usecase.journal.AchievementTier.UNCOMMON -> R.string.tier_uncommon
    com.meteomontana.android.domain.usecase.journal.AchievementTier.RARE -> R.string.tier_rare
    com.meteomontana.android.domain.usecase.journal.AchievementTier.EPIC -> R.string.tier_epic
    com.meteomontana.android.domain.usecase.journal.AchievementTier.LEGENDARY -> R.string.tier_legendary
}

/** Una vez: «¿Escalas sobre todo vías o bloque?». Opcional; se cierra con «Ahora no» y se cambia luego en la hoja. */
@Composable
private fun ClimberTypePrompt(rewards: UserRewards, onChoose: (String) -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("rewards", android.content.Context.MODE_PRIVATE)
    var done by remember { mutableStateOf(prefs.getBoolean(PROMPT_KEY, false) || rewards.climberType != null) }
    if (done) return
    fun close() { prefs.edit().putBoolean(PROMPT_KEY, true).apply(); done = true }
    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.climber_prompt_title), style = EyebrowTextStyle, color = Terra)
        Text(stringResource(R.string.climber_prompt_body), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UserRewards.CLIMBER_TYPES.forEach { t -> ClimberChip(t, false) { onChoose(t); close() } }
            Text(stringResource(R.string.goal_not_now).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                modifier = Modifier.clickable { close() }.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun Choice(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier.clip(CircleShape).background(if (on) terraFillColor() else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) terraFillColor() else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

private val ALL_LEVELS = listOf(0, 1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50)

/** Etiqueta redondeada (dificultad, porcentaje). */
@Composable
private fun TierChip(text: String, color: Color, filled: Boolean) {
    Text(text, color = if (filled) Color.White else color, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 9.5.sp,
        letterSpacing = 0.6.sp,
        modifier = Modifier.clip(CircleShape).background(if (filled) color else color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = if (filled) 0f else 0.5f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun TierPick(title: String, color: Color, on: Boolean, count: Int, onClick: () -> Unit) {
    val fg = if (on) Color.White else color
    Row(
        Modifier.clip(CircleShape).background(if (on) color else color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.7f), CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = fg, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.5.sp)
        Text("$count", color = fg.copy(alpha = 0.85f), fontFamily = Mono, fontSize = 9.5.sp)
    }
}

/** Fila para elegir el logro destacado: franja con el color de su dificultad, texto, y «ÉPICO · 3% de escaladores». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementPickRow(a: com.meteomontana.android.domain.usecase.journal.Achievement, rarity: AchievementRarity?,
                               on: Boolean, onClick: () -> Unit) {
    val color = com.meteomontana.android.ui.screens.profile.tierColor(a.tier)
    val enough = rarity != null && rarity.totalUsers >= MIN_USERS_FOR_PERCENT
    val pct = if (enough) rarity?.percentById?.get(a.id) ?: 0 else 0
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (on) color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .border(if (on) 1.5.dp else 1.dp, if (on) color else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(Modifier.width(5.dp).height(56.dp).background(color))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(com.meteomontana.android.ui.screens.profile.achievementText(a), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TierChip(stringResource(tierNameRes(a.tier)).uppercase(), color, true)
                if (enough) TierChip(if (pct < 1) stringResource(R.string.ach_pct_lt1) else stringResource(R.string.ach_pct, pct), color, false)
            }
        }
        if (on) Icon(Icons.Filled.CheckCircle, null, tint = Terra, modifier = Modifier.padding(end = 12.dp))
    }
}

/** Elegir el título (entre los desbloqueados) y si eres viero, bloquero o las dos. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RewardsSheet(rewards: UserRewards, mineIds: Set<String>?, rarity: AchievementRarity?, onDismiss: () -> Unit,
                         onSave: (String?, Int?, String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.climber_prompt_title), style = EyebrowTextStyle, color = Terra)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (UserRewards.CLIMBER_TYPES + "").forEach { t ->
                    val on = (rewards.climberType ?: "") == t
                    ClimberChip(t.ifEmpty { null }, on) { onSave(t, null, null) }
                }
            }
            Text(stringResource(R.string.rewards_title_pick), style = EyebrowTextStyle, color = Terra)
            Text(stringResource(R.string.rewards_title_hint, rewards.approved), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ALL_LEVELS.forEach { l ->
                    val has = l in rewards.unlockedLevels
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TitlePill(l, on = rewards.titleLevel == l, locked = !has,
                            modifier = if (has) Modifier.clickable { onSave(null, l, null) } else Modifier)
                        if (!has) Text(stringResource(R.string.rewards_needs, l), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        }
    }
}

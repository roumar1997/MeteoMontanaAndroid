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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
        Text(title, color = Terra, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
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
    LaunchedEffect(sheet) { if (sheet) viewModel.loadMineIds() }

    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().then(if (own) Modifier.clickable { sheet = true } else Modifier)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RewardTitle(rewards.titleLevel)
        // El logro que la persona ha elegido enseñar (con su dificultad y el % de gente que lo tiene).
        FeaturedAchievement(rewards.featuredAchievement, rarity)
        if (rewards.badges.isNotEmpty() || rewards.climberType != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rewards.climberType?.let { Badge(climberTypeLabel(it), MaterialTheme.colorScheme.onSurfaceVariant) }
                if ("COLLABORATOR" in rewards.badges) Badge(stringResource(R.string.badge_collaborator), Terra)
                if ("TRUSTED" in rewards.badges) Badge(stringResource(R.string.badge_trusted), Ok)
            }
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
private fun Badge(text: String, color: Color) {
    Text(text.uppercase(), color = color, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        modifier = Modifier.clip(CircleShape).border(1.dp, color, CircleShape).padding(horizontal = 9.dp, vertical = 4.dp))
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
            UserRewards.CLIMBER_TYPES.forEach { t -> Choice(climberTypeLabel(t), false) { onChoose(t); close() } }
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
                    Choice(climberTypeLabel(t.ifEmpty { null }), on) { onSave(t, null, null) }
                }
            }
            Text(stringResource(R.string.rewards_title_pick), style = EyebrowTextStyle, color = Terra)
            Text(stringResource(R.string.rewards_title_hint, rewards.approved), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rewards.unlockedLevels.forEach { l -> Choice(rewardTitle(l), rewards.titleLevel == l) { onSave(null, l, null) } }
            }
            // Logro destacado: uno de los desbloqueados, el más raro primero.
            Text(stringResource(R.string.featured_pick), style = EyebrowTextStyle, color = Terra)
            val unlocked = mineIds?.let { ids -> AchievementsCalculator.catalog.filter { it.id in ids }.sortedByDescending { it.tier.ordinal } }
            when {
                mineIds == null -> Text(stringResource(R.string.featured_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                unlocked.isNullOrEmpty() -> Text(stringResource(R.string.featured_none_synced), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(stringResource(R.string.featured_none), rewards.featuredAchievement == null) { onSave(null, null, "") }
                    unlocked.forEach { a ->
                        Choice("★ " + com.meteomontana.android.ui.screens.profile.achievementText(a), rewards.featuredAchievement == a.id) {
                            onSave(null, null, a.id)
                        }
                    }
                }
            }
        }
    }
}

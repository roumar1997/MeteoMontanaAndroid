package com.meteomontana.android.ui.screens.users
import com.meteomontana.android.util.toUserMessage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.FollowStatus
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.model.SchoolStats
import com.meteomontana.android.domain.usecase.journal.GetUserStatsUseCase
import com.meteomontana.android.domain.usecase.social.FollowUserUseCase
import com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import com.meteomontana.android.domain.usecase.social.UnfollowUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PublicProfileUiState {
    data object Loading : PublicProfileUiState
    data class Success(
        val profile: PublicProfile,
        val status: FollowStatus,
        val stats: JournalStats? = null
    ) : PublicProfileUiState
    data class Error(val message: String) : PublicProfileUiState
}

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPublicProfile: GetPublicProfileUseCase,
    private val getFollowStatus: GetFollowStatusUseCase,
    private val getUserStats: GetUserStatsUseCase,
    private val followUser: FollowUserUseCase,
    private val unfollowUser: UnfollowUserUseCase
) : ViewModel() {
    private val uid: String = checkNotNull(savedStateHandle["uid"])
    private val _state = MutableStateFlow<PublicProfileUiState>(PublicProfileUiState.Loading)
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = PublicProfileUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                // Perfil y follow status en paralelo (ambos solo necesitan uid).
                coroutineScope {
                    val profileD = async { getPublicProfile(uid) }
                    val statusD = async {
                        runCatching { getFollowStatus(uid) }
                            .getOrDefault(FollowStatus(0, 0, false, false, false))
                    }
                    val profile = profileD.await()
                    val stats = if (profile.locked) null
                                else runCatching { getUserStats(uid) }.getOrNull()
                    PublicProfileUiState.Success(profile, statusD.await(), stats)
                }
            } catch (t: Throwable) {
                PublicProfileUiState.Error(t.toUserMessage())
            }
        }
    }

    fun toggleFollow() {
        val cur = _state.value as? PublicProfileUiState.Success ?: return
        viewModelScope.launch {
            try {
                if (cur.status.iFollowThem) unfollowUser(uid) else followUser(uid)
                val newStatus = getFollowStatus(uid)
                val newProfile = runCatching { getPublicProfile(uid) }.getOrDefault(cur.profile)
                val newStats = if (newProfile.locked) null
                               else runCatching { getUserStats(uid) }.getOrNull()
                _state.value = PublicProfileUiState.Success(newProfile, newStatus, newStats)
            } catch (_: Throwable) {}
        }
    }
}

@Composable
fun PublicProfileScreen(
    onBack: () -> Unit,
    onFollowersClick: (String) -> Unit = {},
    onFollowingClick: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onOpenBoulders: (String) -> Unit = {},
    onOpenRoutes: (String) -> Unit = {},
    onOpenMaxGrade: (String) -> Unit = {},
    onOpenSchools: (String) -> Unit = {},
    onOpenSchoolEntries: (uid: String, schoolName: String) -> Unit = { _, _ -> },
    viewModel: PublicProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Perfil", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            PublicProfileUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is PublicProfileUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
            }
            is PublicProfileUiState.Success -> Body(
                s, viewModel::toggleFollow,
                onFollowersClick = { onFollowersClick(s.profile.uid) },
                onFollowingClick = { onFollowingClick(s.profile.uid) },
                onMessage = { onOpenChat(s.profile.uid) },
                onOpenBoulders = { onOpenBoulders(s.profile.uid) },
                onOpenRoutes = { onOpenRoutes(s.profile.uid) },
                onOpenMaxGrade = { onOpenMaxGrade(s.profile.uid) },
                onOpenSchools = { onOpenSchools(s.profile.uid) },
                onOpenSchoolEntries = { schoolName -> onOpenSchoolEntries(s.profile.uid, schoolName) }
            )
        }
    }
}

@Composable
private fun Body(
    s: PublicProfileUiState.Success,
    onToggleFollow: () -> Unit,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onMessage: () -> Unit,
    onOpenBoulders: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    onOpenMaxGrade: () -> Unit = {},
    onOpenSchools: () -> Unit = {},
    onOpenSchoolEntries: (String) -> Unit = {}
) {
    val p = s.profile
    val locked = p.locked
    Column(modifier = Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)) {
        // Cabecera centrada, igual que el perfil propio.
        Column(modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (p.photoUrl != null) {
                AsyncImage(model = p.photoUrl, contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            } else {
                Image(painter = painterResource(R.drawable.logo_cumbre), contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            Text(p.displayName ?: p.username ?: "Usuario",
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("@${p.username ?: p.displayName?.lowercase()?.replace(" ", "_") ?: "usuario"}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!locked) {
                p.bio?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            if (!locked && !p.topGrade.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("TOPE ${p.topGrade}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                FollowCount(s.status.followers, "SEGUIDORES",
                    if (locked) ({}) else onFollowersClick)
                FollowCount(s.status.following, "SIGUIENDO",
                    if (locked) ({}) else onFollowingClick)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FollowButton(
                    iFollow = s.status.iFollowThem,
                    requestPending = s.status.requestPending,
                    onClick = onToggleFollow
                )
            }
            if (!locked) {
                Box(modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .clickable(onClick = onMessage),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Mensaje", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        if (s.status.theyFollowMe && !locked) {
            Spacer(Modifier.height(8.dp))
            Text("Te sigue", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp))
        }
        if (!locked && s.stats != null) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            ActivityStatsRow(
                stats = s.stats,
                onBlocksClick = onOpenBoulders,
                onRoutesClick = onOpenRoutes,
                onSchoolsClick = onOpenSchools,
                onMaxClick = onOpenMaxGrade
            )
            if (s.stats.bySchool.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("ESCUELAS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(8.dp))
                s.stats.bySchool.forEach { school ->
                    SchoolStatRow(school, onClick = { onOpenSchoolEntries(school.schoolName) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        if (locked) {
            Spacer(Modifier.height(32.dp))
            Column(modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Perfil privado", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(4.dp))
                Text("Sigue a este usuario para ver su perfil.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActivityStatsRow(
    stats: JournalStats,
    onBlocksClick: () -> Unit,
    onRoutesClick: () -> Unit,
    onSchoolsClick: () -> Unit,
    onMaxClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("BLOQUES", stats.boulderCount.toString(), Modifier.weight(1f), onBlocksClick)
            StatBox("VÍAS", stats.routeCount.toString(), Modifier.weight(1f), onRoutesClick)
            StatBox("ESCUELAS", stats.schoolCount.toString(), Modifier.weight(1f), onSchoolsClick)
        }
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("MÁX BLOQUE", stats.maxBoulderGrade ?: "—", Modifier.weight(1f), onMaxClick)
            StatBox("MÁX VÍA", stats.maxRouteGrade ?: "—", Modifier.weight(1f), onMaxClick)
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Mismo estilo que el StatCell del perfil propio (fondo surface + borde + serif).
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FollowCount(value: Long, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$value", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SchoolStatRow(school: SchoolStats, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(school.schoolName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground)
            Text("${school.blockCount} bloque${if (school.blockCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(school.maxGrade ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FollowButton(iFollow: Boolean, requestPending: Boolean, onClick: () -> Unit) {
    val text = when {
        iFollow         -> "Dejar de seguir"
        requestPending  -> "Solicitud enviada"
        else            -> "Seguir"
    }
    val filled = !iFollow && !requestPending
    val bg = if (filled) Color(0xFF1C1C1A) else MaterialTheme.colorScheme.surface
    val fg = if (filled) Color.White else MaterialTheme.colorScheme.onBackground
    val borderColor = if (filled) Color(0xFF1C1C1A) else MaterialTheme.colorScheme.outline
    Box(modifier = Modifier.fillMaxWidth().height(48.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(bg, RoundedCornerShape(2.dp))
        .border(1.dp, borderColor, RoundedCornerShape(2.dp))
        .clickable(enabled = !requestPending, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

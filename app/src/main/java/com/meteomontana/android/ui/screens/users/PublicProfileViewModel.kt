package com.meteomontana.android.ui.screens.users

import com.meteomontana.android.ui.components.SkeletonProfile
import com.meteomontana.android.ui.components.userRing
import com.meteomontana.android.ui.components.VerifiedCheck
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.HowToReg
import com.meteomontana.android.ui.theme.inkButtonColor
import com.meteomontana.android.util.toUserMessage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.meteomontana.android.domain.usecase.admin.GetUserAssistantAccessUseCase
import com.meteomontana.android.domain.usecase.admin.SetUserAssistantAccessUseCase
import com.meteomontana.android.domain.usecase.journal.GetUserStatsUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
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

// Estado y ViewModel del perfil público (separados de la pantalla).

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
    private val unfollowUser: UnfollowUserUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    private val getUserAssistantAccess: GetUserAssistantAccessUseCase,
    private val setUserAssistantAccess: SetUserAssistantAccessUseCase
) : ViewModel() {
    private val uid: String = checkNotNull(savedStateHandle["uid"])
    private val _state = MutableStateFlow<PublicProfileUiState>(PublicProfileUiState.Loading)
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    /** Interruptor del asistente de IA de ESTE usuario. null = no se enseña (no eres admin, o es tu propio perfil). */
    private val _adminAssistant = MutableStateFlow<AdminAssistantUi?>(null)
    val adminAssistant: StateFlow<AdminAssistantUi?> = _adminAssistant.asStateFlow()

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
            (_state.value as? PublicProfileUiState.Success)?.let { loadAdminAssistant(it.profile.uid) }
        }
    }

    /** Solo si quien mira es admin (y no es su propio perfil): lee el interruptor del asistente de IA de este usuario. */
    private suspend fun loadAdminAssistant(profileUid: String) {
        _adminAssistant.value = null
        val me = runCatching { getMyProfile() }.getOrNull() ?: return
        if (!me.isAdmin || me.uid == profileUid) return
        runCatching { getUserAssistantAccess(profileUid) }
            .onSuccess { _adminAssistant.value = AdminAssistantUi(it) }
    }

    /** Activa o desactiva el asistente de IA de este usuario. Si falla, el interruptor vuelve a su sitio. */
    fun setAssistantAccess(enabled: Boolean) {
        val profileUid = (_state.value as? PublicProfileUiState.Success)?.profile?.uid ?: return
        val before = _adminAssistant.value ?: return
        _adminAssistant.value = AdminAssistantUi(enabled, saving = true)
        viewModelScope.launch {
            _adminAssistant.value = runCatching { setUserAssistantAccess(profileUid, enabled) }
                .fold({ AdminAssistantUi(it) }, { before })
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

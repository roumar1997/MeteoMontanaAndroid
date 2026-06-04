package com.meteomontana.android.ui.screens.users

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.FollowStatusDto
import com.meteomontana.android.data.api.dto.PublicProfileDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PublicProfileUiState {
    data object Loading : PublicProfileUiState
    data class Success(val profile: PublicProfileDto, val status: FollowStatusDto) : PublicProfileUiState
    data class Error(val message: String) : PublicProfileUiState
}

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: SchoolApi
) : ViewModel() {
    private val uid: String = checkNotNull(savedStateHandle["uid"])
    private val _state = MutableStateFlow<PublicProfileUiState>(PublicProfileUiState.Loading)
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = PublicProfileUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val profile = api.getUserProfile(uid)
                val status = runCatching { api.getFollowStatus(uid) }
                    .getOrDefault(FollowStatusDto(0, 0, false, false))
                PublicProfileUiState.Success(profile, status)
            } catch (t: Throwable) {
                PublicProfileUiState.Error(t.message ?: "Error")
            }
        }
    }

    fun toggleFollow() {
        val cur = _state.value as? PublicProfileUiState.Success ?: return
        viewModelScope.launch {
            try {
                if (cur.status.iFollowThem) api.unfollow(uid)
                else api.follow(uid)
                val newStatus = api.getFollowStatus(uid)
                _state.value = cur.copy(status = newStatus)
            } catch (_: Throwable) {}
        }
    }
}

@Composable
fun PublicProfileScreen(
    onBack: () -> Unit,
    onFollowersClick: (String) -> Unit = {},
    onFollowingClick: (String) -> Unit = {},
    viewModel: PublicProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null,
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
                onFollowingClick = { onFollowingClick(s.profile.uid) }
            )
        }
    }
}

@Composable
private fun Body(
    s: PublicProfileUiState.Success,
    onToggleFollow: () -> Unit,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit
) {
    val p = s.profile
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.photoUrl != null) {
                AsyncImage(model = p.photoUrl, contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            } else {
                Image(painter = painterResource(R.drawable.logo_cumbre), contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            }
            Spacer(Modifier.padding(start = 16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("@${p.username ?: p.displayName ?: "usuario"}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground)
                p.bio?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Seguidores", s.status.followers.toString(),
                modifier = Modifier.clickable(onClick = onFollowersClick))
            Stat("Siguiendo", s.status.following.toString(),
                modifier = Modifier.clickable(onClick = onFollowingClick))
            Stat("Grado máx", p.topGrade ?: "—")
        }
        Spacer(Modifier.height(24.dp))
        FollowButton(s.status.iFollowThem, onToggleFollow)
        if (s.status.theyFollowMe) {
            Spacer(Modifier.height(8.dp))
            Text("Te sigue", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp))
        }
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
private fun FollowButton(iFollow: Boolean, onClick: () -> Unit) {
    val bg = if (iFollow) MaterialTheme.colorScheme.surface else Color(0xFF1C1C1A)
    val fg = if (iFollow) MaterialTheme.colorScheme.onBackground else Color.White
    Box(modifier = Modifier.fillMaxWidth().height(48.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(bg, RoundedCornerShape(2.dp))
        .border(1.dp, if (iFollow) MaterialTheme.colorScheme.outline else Color(0xFF1C1C1A), RoundedCornerShape(2.dp))
        .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(if (iFollow) "Dejar de seguir" else "Seguir",
            style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

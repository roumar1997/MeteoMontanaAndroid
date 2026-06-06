package com.meteomontana.android.ui.screens.users
import com.meteomontana.android.util.toUserMessage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.data.api.SocialApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.PublicProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FollowListUiState {
    data object Loading : FollowListUiState
    data class Success(val items: List<PublicProfile>) : FollowListUiState
    data class Error(val message: String) : FollowListUiState
}

@HiltViewModel
class FollowListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: SocialApi
) : ViewModel() {
    private val uid: String = checkNotNull(savedStateHandle["uid"])
    private val mode: String = checkNotNull(savedStateHandle["mode"]) // "followers" | "following"

    private val _state = MutableStateFlow<FollowListUiState>(FollowListUiState.Loading)
    val state: StateFlow<FollowListUiState> = _state.asStateFlow()

    val title: String = if (mode == "followers") "Seguidores" else "Siguiendo"

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                val list = (if (mode == "followers") api.getFollowers(uid) else api.getFollowing(uid)).map { it.toDomain() }
                FollowListUiState.Success(list)
            } catch (t: Throwable) {
                FollowListUiState.Error(t.toUserMessage())
            }
        }
    }
}

@Composable
fun FollowListScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: FollowListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(viewModel.title, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            FollowListUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is FollowListUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is FollowListUiState.Success -> {
                if (s.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Vacío", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn {
                        items(s.items) { u ->
                            UserRow(u) { onUserClick(u.uid) }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(u: PublicProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (u.photoUrl != null) {
            AsyncImage(model = u.photoUrl, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        } else {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("@${u.username ?: u.displayName ?: u.uid.take(6)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            if (!u.bio.isNullOrBlank()) {
                Text(u.bio, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!u.topGrade.isNullOrBlank()) {
            Text(u.topGrade, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

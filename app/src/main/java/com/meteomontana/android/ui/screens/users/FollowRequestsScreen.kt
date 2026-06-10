package com.meteomontana.android.ui.screens.users

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.PublicProfile
import com.meteomontana.android.domain.usecase.social.AcceptFollowRequestUseCase
import com.meteomontana.android.domain.usecase.social.GetMyFollowRequestsUseCase
import com.meteomontana.android.domain.usecase.social.RejectFollowRequestUseCase
import com.meteomontana.android.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FollowRequestsUiState {
    data object Loading : FollowRequestsUiState
    data class Success(val items: List<PublicProfile>) : FollowRequestsUiState
    data class Error(val message: String) : FollowRequestsUiState
}

@HiltViewModel
class FollowRequestsViewModel @Inject constructor(
    private val getMyRequests: GetMyFollowRequestsUseCase,
    private val accept: AcceptFollowRequestUseCase,
    private val reject: RejectFollowRequestUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<FollowRequestsUiState>(FollowRequestsUiState.Loading)
    val state: StateFlow<FollowRequestsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = FollowRequestsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                FollowRequestsUiState.Success(getMyRequests())
            } catch (t: Throwable) {
                FollowRequestsUiState.Error(t.toUserMessage())
            }
        }
    }

    fun accept(uid: String) {
        viewModelScope.launch {
            runCatching { accept.invoke(uid) }
            removeFromList(uid)
        }
    }

    fun reject(uid: String) {
        viewModelScope.launch {
            runCatching { reject.invoke(uid) }
            removeFromList(uid)
        }
    }

    private fun removeFromList(uid: String) {
        val cur = _state.value as? FollowRequestsUiState.Success ?: return
        _state.value = FollowRequestsUiState.Success(cur.items.filter { it.uid != uid })
    }
}

@Composable
fun FollowRequestsScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit = {},
    viewModel: FollowRequestsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Solicitudes", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            FollowRequestsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is FollowRequestsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
            }
            is FollowRequestsUiState.Success -> {
                if (s.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("No tienes solicitudes pendientes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(s.items, key = { it.uid }) { p ->
                            RequestRow(
                                profile = p,
                                onClick = { onUserClick(p.uid) },
                                onAccept = { viewModel.accept(p.uid) },
                                onReject = { viewModel.reject(p.uid) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(
    profile: PublicProfile,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (profile.photoUrl != null) {
            AsyncImage(model = profile.photoUrl, contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        } else {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("@${profile.username ?: profile.displayName ?: "usuario"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text("quiere seguirte",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SmallButton(text = "Aceptar", filled = true, onClick = onAccept)
        SmallButton(text = "Rechazar", filled = false, onClick = onReject)
    }
}

@Composable
private fun SmallButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) Color(0xFF1C1C1A) else MaterialTheme.colorScheme.surface
    val fg = if (filled) Color.White else MaterialTheme.colorScheme.onBackground
    val borderColor = if (filled) Color(0xFF1C1C1A) else MaterialTheme.colorScheme.outline
    Box(modifier = Modifier
        .height(36.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(bg, RoundedCornerShape(2.dp))
        .border(1.dp, borderColor, RoundedCornerShape(2.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

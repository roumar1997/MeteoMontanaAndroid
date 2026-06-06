package com.meteomontana.android.ui.screens.notifications
import com.meteomontana.android.util.toUserMessage

import androidx.compose.foundation.background
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.NotificationApi
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.Inbox
import com.meteomontana.android.domain.model.Notification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState
    data class Success(val inbox: Inbox) : NotificationsUiState
    data class Error(val message: String) : NotificationsUiState
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: NotificationApi
) : ViewModel() {
    private val _state = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                NotificationsUiState.Success(api.getMyNotifications().toDomain())
            } catch (t: Throwable) {
                NotificationsUiState.Error(t.toUserMessage())
            }
        }
    }

    fun markAllRead() {
        // Optimistic: actualizar UI inmediatamente
        val cur = _state.value
        if (cur is NotificationsUiState.Success) {
            val now = java.time.LocalDateTime.now().toString()
            val updated = cur.inbox.copy(
                unreadCount = 0,
                items = cur.inbox.items.map { if (it.readAt == null) it.copy(readAt = now) else it }
            )
            _state.value = NotificationsUiState.Success(updated)
        }
        viewModelScope.launch {
            runCatching { api.markAllNotificationsRead() }
        }
    }

    fun onItemClick(id: String) {
        // Optimistic: marcar leída en UI antes de la network call
        val cur = _state.value
        if (cur is NotificationsUiState.Success) {
            val now = java.time.LocalDateTime.now().toString()
            val updated = cur.inbox.copy(
                items = cur.inbox.items.map {
                    if (it.id == id && it.readAt == null) it.copy(readAt = now) else it
                },
                unreadCount = (cur.inbox.unreadCount - 1).coerceAtLeast(0)
            )
            _state.value = NotificationsUiState.Success(updated)
        }
        viewModelScope.launch {
            runCatching { api.markNotificationRead(id) }
        }
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                Text("Notificaciones",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            TextButton(onClick = viewModel::markAllRead) {
                Text("Marcar leído",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            NotificationsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is NotificationsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is NotificationsUiState.Success -> {
                if (s.inbox.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("No tienes notificaciones",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(s.inbox.items) { n ->
                            NotificationRow(n) {
                                viewModel.onItemClick(n.id)
                                val tid = n.targetId
                                if (n.targetType == "user" && tid != null) onOpenUser(tid)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: Notification, onClick: () -> Unit) {
    val unread = n.readAt == null
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape)
            .background(if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
        Column(modifier = Modifier.weight(1f)) {
            Text(n.title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            n.body?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

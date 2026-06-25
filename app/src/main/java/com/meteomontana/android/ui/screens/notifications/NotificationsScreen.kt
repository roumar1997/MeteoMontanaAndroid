package com.meteomontana.android.ui.screens.notifications
import com.meteomontana.android.util.toUserMessage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.Inbox
import com.meteomontana.android.domain.model.Notification
import com.meteomontana.android.domain.usecase.notifications.GetMyNotificationsUseCase
import com.meteomontana.android.domain.usecase.notifications.MarkAllNotificationsReadUseCase
import com.meteomontana.android.domain.usecase.notifications.MarkNotificationReadUseCase
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
    private val getMyNotifications: GetMyNotificationsUseCase,
    private val markNotificationRead: MarkNotificationReadUseCase,
    private val markAllNotificationsRead: MarkAllNotificationsReadUseCase,
    private val deleteNotification: com.meteomontana.android.domain.usecase.notifications.DeleteNotificationUseCase,
    private val deleteAllNotifications: com.meteomontana.android.domain.usecase.notifications.DeleteAllNotificationsUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                NotificationsUiState.Success(getMyNotifications())
            } catch (t: Throwable) {
                NotificationsUiState.Error(t.toUserMessage())
            }
        }
    }

    fun markAllRead() {
        val cur = _state.value
        if (cur is NotificationsUiState.Success) {
            val now = java.time.LocalDateTime.now().toString()
            val updated = cur.inbox.copy(
                unreadCount = 0,
                items = cur.inbox.items.map { if (it.readAt == null) it.copy(readAt = now) else it }
            )
            _state.value = NotificationsUiState.Success(updated)
        }
        viewModelScope.launch { runCatching { markAllNotificationsRead() } }
    }

    /** Borra una notificación (optimista) y la sincroniza con el backend. */
    fun delete(id: String) {
        val cur = _state.value
        if (cur is NotificationsUiState.Success) {
            val removed = cur.inbox.items.firstOrNull { it.id == id }
            val wasUnread = removed?.readAt == null
            _state.value = NotificationsUiState.Success(cur.inbox.copy(
                items = cur.inbox.items.filter { it.id != id },
                unreadCount = if (wasUnread) (cur.inbox.unreadCount - 1).coerceAtLeast(0) else cur.inbox.unreadCount
            ))
        }
        viewModelScope.launch { runCatching { deleteNotification(id) } }
    }

    /** Borra TODAS las notificaciones (optimista) y lo sincroniza. */
    fun deleteAll() {
        val cur = _state.value
        if (cur is NotificationsUiState.Success) {
            _state.value = NotificationsUiState.Success(cur.inbox.copy(items = emptyList(), unreadCount = 0))
        }
        viewModelScope.launch { runCatching { deleteAllNotifications() } }
    }

    fun onItemClick(id: String) {
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
        viewModelScope.launch { runCatching { markNotificationRead(id) } }
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit = {},
    onOpenSchool: (String) -> Unit = {},
    onOpenSubmissions: () -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onOpenFollowRequests: () -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDeleteAll by remember { mutableStateOf(false) }

    // Al salir de la bandeja, marca todas como leídas (ya las has visto) → el
    // badge de la campana desaparece al volver.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.markAllRead() }
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("¿Borrar todas?") },
            text = { Text("Se eliminarán todas tus notificaciones. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { showDeleteAll = false; viewModel.deleteAll() }) {
                    Text("Borrar todas", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAll = false }) { Text("Cancelar") } }
        )
    }

    // ¿Hay notificaciones? Solo entonces tienen sentido las acciones
    // "Marcar leído" / "Borrar todas" (si está vacío, se ocultan).
    val hasItems = (state as? NotificationsUiState.Success)?.inbox?.items?.isNotEmpty() == true

    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        // Cabecera de sheet (título centrado + "Cerrar"), como Cuenta.
        com.meteomontana.android.ui.components.SheetHeader("Notificaciones", onClose = onBack)
        // Acciones en su propia fila a la derecha, solo si hay algo que tocar.
        if (hasItems) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = viewModel::markAllRead) {
                    Text("Marcar leído",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = { showDeleteAll = true }) {
                    Text("Borrar todas",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        when (val s = state) {
            NotificationsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is NotificationsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is NotificationsUiState.Success -> {
                if (s.inbox.items.isEmpty()) {
                    com.meteomontana.android.ui.components.EmptyState(
                        icon = Icons.Outlined.Notifications,
                        title = "Sin notificaciones",
                        message = "Aquí te avisaremos de nuevos seguidores, solicitudes, mensajes y novedades de tus propuestas."
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(s.inbox.items, key = { it.id }) { n ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { target ->
                                    if (target != SwipeToDismissBoxValue.Settled) {
                                        viewModel.delete(n.id); true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd) {
                                        Text("Borrar", color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            ) {
                                NotificationRow(n) {
                                    viewModel.onItemClick(n.id)
                                    val tid = n.targetId
                                    when (n.targetType) {
                                        "user"          -> tid?.let(onOpenUser)
                                        "school", "school_detail" -> tid?.let(onOpenSchool)
                                        "submission", "contribution" -> onOpenSubmissions()
                                        "chat", "message" -> tid?.let(onOpenChat)
                                        "follow_request" -> onOpenFollowRequests()
                                        else            -> {}
                                    }
                                }
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
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
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

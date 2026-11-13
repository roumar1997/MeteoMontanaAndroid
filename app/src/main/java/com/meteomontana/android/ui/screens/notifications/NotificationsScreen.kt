package com.meteomontana.android.ui.screens.notifications
import com.meteomontana.android.ui.components.SkeletonList
import com.meteomontana.android.ui.components.SkeletonPersonRow
import com.meteomontana.android.util.toUserMessage
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.R
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
    private val deleteAllNotifications: com.meteomontana.android.domain.usecase.notifications.DeleteAllNotificationsUseCase,
    private val followUser: com.meteomontana.android.domain.usecase.social.FollowUserUseCase,
    private val unfollowUser: com.meteomontana.android.domain.usecase.social.UnfollowUserUseCase,
    private val getFollowStatus: com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase
) : ViewModel() {
    /** Lo que he tocado en los botones de seguir (uid -> estado); si no, manda lo que dijo el servidor. */
    private val _follow = MutableStateFlow<Map<String, FollowUi>>(emptyMap())
    val follow: StateFlow<Map<String, FollowUi>> = _follow.asStateFlow()

    /** Seguir / dejar de seguir a quien me acaba de seguir, sin salir de la bandeja. */
    internal fun toggleFollow(uid: String, current: FollowUi) {
        fun set(v: FollowUi) { _follow.value = _follow.value + (uid to v) }
        if (current == FollowUi.REQUESTED) return
        if (current == FollowUi.FOLLOWING) {
            set(FollowUi.NONE)
            viewModelScope.launch { runCatching { unfollowUser(uid) }.onFailure { set(FollowUi.FOLLOWING) } }
        } else {
            set(FollowUi.FOLLOWING)
            viewModelScope.launch {
                runCatching { followUser(uid); getFollowStatus(uid) }
                    .onSuccess { st -> set(when { st.iFollowThem -> FollowUi.FOLLOWING; st.requestPending -> FollowUi.REQUESTED; else -> FollowUi.NONE }) }
                    .onFailure { set(FollowUi.NONE) }
            }
        }
    }

    /** Borra varios avisos de golpe (un grupo de me gusta) y lo sincroniza. */
    fun deleteMany(ids: List<String>) { ids.forEach(::delete) }

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
    onOpenFeedPost: (String) -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteAll by remember { mutableStateOf(false) }

    // Al salir de la bandeja, marca todas como leídas (ya las has visto) → el
    // badge de la campana desaparece al volver.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.markAllRead() }
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text(stringResource(R.string.notifications_screen_borrar_todas)) },
            text = { Text(stringResource(R.string.notifications_screen_se_eliminaran_todas_tus)) },
            confirmButton = {
                TextButton(onClick = { showDeleteAll = false; viewModel.deleteAll() }) {
                    Text(stringResource(R.string.notifications_delete_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAll = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    // ¿Hay notificaciones? Solo entonces tienen sentido las acciones
    // "Marcar leído" / "Borrar todas" (si está vacío, se ocultan).
    val hasItems = (state as? NotificationsUiState.Success)?.inbox?.items?.isNotEmpty() == true

    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        // Cabecera de sheet (título centrado + "Cerrar"), como Cuenta.
        com.meteomontana.android.ui.components.SheetHeader(stringResource(R.string.notifications_title), onClose = onBack)
        // Acciones en su propia fila a la derecha, solo si hay algo que tocar.
        if (hasItems) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = viewModel::markAllRead) {
                    Text(stringResource(R.string.notifications_mark_read),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = { showDeleteAll = true }) {
                    Text(stringResource(R.string.notifications_delete_all),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        when (val s = state) {
            NotificationsUiState.Loading -> SkeletonList(count = 8) { SkeletonPersonRow(avatar = 36.dp) }
            is NotificationsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is NotificationsUiState.Success -> {
                if (s.inbox.items.isEmpty()) {
                    com.meteomontana.android.ui.components.EmptyState(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.notifications_empty),
                        message = stringResource(R.string.notifications_screen_v3_aqui_te_avisaremos_de_nuevos)
                    )
                } else {
                    val groups = remember(s.inbox.items) {
                        com.meteomontana.android.domain.usecase.notifications.NotificationGrouping.group(s.inbox.items)
                    }
                    val followMap by viewModel.follow.collectAsStateWithLifecycle()
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(groups.size, key = { groups[it].id }) { idx ->
                            val g = groups[idx]
                            val n = g.lead
                            if (idx == 0 || groups[idx - 1].section != g.section) {
                                NotificationSectionHeader(stringResource(sectionLabelRes(g.section)))
                            }
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { target ->
                                    if (target != SwipeToDismissBoxValue.Settled) {
                                        viewModel.deleteMany(g.memberIds); true
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
                                        Text(stringResource(R.string.notifications_screen_v2_borrar), color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            ) {
                                val follow = n.actorUid?.let { followMap[it] }
                                    ?: if (n.iFollowActor) FollowUi.FOLLOWING else if (n.followRequested) FollowUi.REQUESTED else FollowUi.NONE
                                NotificationRow(g, follow, onToggleFollow = { n.actorUid?.let { viewModel.toggleFollow(it, follow) } }) {
                                    g.memberIds.forEach(viewModel::onItemClick)
                                    val tid = n.targetId
                                    when (n.targetType) {
                                        "user"          -> tid?.let(onOpenUser)
                                        "school", "school_detail" -> tid?.let(onOpenSchool)
                                        "submission", "contribution" -> onOpenSubmissions()
                                        "chat", "message" -> tid?.let(onOpenChat)
                                        "follow_request" -> onOpenFollowRequests()
                                        // Actividad del feed Comunidad → detalle del post.
                                        "feed_post"     -> tid?.let(onOpenFeedPost)
                                        else            -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

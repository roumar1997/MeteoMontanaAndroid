package com.meteomontana.android.ui.screens.chat

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@Composable
fun ChatListScreen(
    onBack: () -> Unit,
    onOpenChat: (otherUid: String) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Chats", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Aún no tienes conversaciones",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(items, key = { it.conversation.id }) { item ->
                    val other = item.otherProfile
                    val otherUid = item.conversation.participants.firstOrNull {
                        it != com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    } ?: return@items
                    SwipeableConvRow(
                        avatarUrl = other?.photoUrl,
                        name = other?.username ?: other?.displayName ?: otherUid.take(6),
                        lastMessage = item.conversation.lastMessage ?: "",
                        unread = item.conversation.unreadCount,
                        onClick = { onOpenChat(otherUid) },
                        onDelete = { viewModel.deleteConversation(item.conversation.id) },
                        onMarkUnread = { viewModel.markUnread(item.conversation.id) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/**
 * Fila con swipe estilo WhatsApp: arrastrar a la IZQUIERDA borra el chat;
 * arrastrar a la DERECHA lo marca como no leído (vuelve el badge).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableConvRow(
    avatarUrl: String?,
    name: String,
    lastMessage: String,
    unread: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMarkUnread: () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }       // izquierda → borrar
                SwipeToDismissBoxValue.StartToEnd -> { onMarkUnread(); false }  // derecha → no leído (no quita la fila)
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> SwipeBg(
                    color = MaterialTheme.colorScheme.errorContainer,
                    icon = Icons.Outlined.Delete, label = "Borrar",
                    alignment = Alignment.CenterEnd,
                    tint = MaterialTheme.colorScheme.onErrorContainer)
                SwipeToDismissBoxValue.StartToEnd -> SwipeBg(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    icon = Icons.Outlined.Email, label = "No leído",
                    alignment = Alignment.CenterStart,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                else -> {}
            }
        }
    ) {
        ConvRow(avatarUrl = avatarUrl, name = name, lastMessage = lastMessage,
            unread = unread, onClick = onClick)
    }
}

@Composable
private fun SwipeBg(
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    alignment: Alignment,
    tint: androidx.compose.ui.graphics.Color
) {
    Box(
        Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = label, tint = tint)
            Text(label, color = tint, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ConvRow(
    avatarUrl: String?,
    name: String,
    lastMessage: String,
    unread: Long,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (avatarUrl != null) {
            AsyncImage(model = avatarUrl, contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    com.meteomontana.android.R.drawable.logo_cumbre),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("@$name", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text(lastMessage, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
        }
        if (unread > 0) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(if (unread > 9) "9+" else unread.toString(),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

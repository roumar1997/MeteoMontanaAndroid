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
                Icon(Icons.Outlined.ArrowBack, contentDescription = null,
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
                items(items) { item ->
                    val other = item.otherProfile
                    val otherUid = item.conversation.participants.firstOrNull {
                        it != com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    } ?: return@items
                    ConvRow(
                        avatarUrl = other?.photoUrl,
                        name = other?.username ?: other?.displayName ?: otherUid.take(6),
                        lastMessage = item.conversation.lastMessage ?: "",
                        unread = item.conversation.unreadCount,
                        onClick = { onOpenChat(otherUid) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
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

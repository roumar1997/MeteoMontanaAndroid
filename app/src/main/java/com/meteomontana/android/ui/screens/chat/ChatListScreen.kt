package com.meteomontana.android.ui.screens.chat


import com.meteomontana.android.ui.components.dismissKeyboardOnScroll
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import com.meteomontana.android.domain.model.PublicProfile
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

@Composable
fun ChatListScreen(
    onBack: () -> Unit,
    onOpenChat: (otherUid: String) -> Unit,
    onOpenGroup: (convId: String) -> Unit = {},
    onNewGroup: () -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        com.meteomontana.android.ui.components.SheetHeader(
            title = stringResource(R.string.chat_list_screen_v2_chats),
            onClose = onBack,
            leading = {
                com.meteomontana.android.ui.components.HelpButton(topicKey = "chat", compact = true)
            },
            actions = {
                IconButton(onClick = onNewGroup) {
                    Icon(Icons.Outlined.GroupAdd, contentDescription = stringResource(R.string.chat_list_screen_v2_nuevo_grupo),
                        tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.loadContacts(); showPicker = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.chat_list_screen_v2_nuevo_mensaje_2),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        )

        if (showPicker) {
            NewChatDialog(
                contacts = contacts,
                onDismiss = { showPicker = false },
                onPick = { uid -> showPicker = false; onOpenChat(uid) }
            )
        }

        if (items.isEmpty()) {
            com.meteomontana.android.ui.components.EmptyState(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = stringResource(R.string.chat_list_screen_v2_aun_no_tienes_conversaciones),
                message = stringResource(R.string.chat_list_screen_v3_toca_el_lapiz_para_escribir)
            )
        } else {
            // Buscador + filtros (Álvaro, 2026-10-22; espejo de ChatListView.swift): con muchos chats ya costaba encontrar uno.
            var query by remember { mutableStateOf("") }
            var filter by remember { mutableStateOf(ChatFilter.All) }
            val groupName = stringResource(R.string.w_group)
            fun titleOf(item: ChatListItem): String {
                val conv = item.conversation
                if (conv.isGroup) return conv.name ?: groupName
                val other = item.otherProfile
                return other?.username ?: other?.displayName ?: ""
            }
            val q = query.trim().lowercase()
            val shown = items.filter { item ->
                val conv = item.conversation
                val passes = when (filter) {
                    ChatFilter.All -> true
                    ChatFilter.Unread -> conv.unreadCount > 0
                    ChatFilter.Groups -> conv.isGroup
                }
                passes && (q.isEmpty() || titleOf(item).lowercase().contains(q) ||
                    (conv.lastMessage ?: "").lowercase().contains(q))
            }
            LazyColumn {
                item {
                    com.meteomontana.android.ui.components.FirstTimeHint(
                        hintKey = "chat_swipe",
                        text = stringResource(R.string.chat_list_screen_v3_desliza_una_conversacion_a_la)
                    )
                }
                item {
                    ChatSearchBar(
                        query = query, onQuery = { query = it },
                        filter = filter, onFilter = { filter = it },
                        counts = mapOf(
                            ChatFilter.All to items.size,
                            ChatFilter.Unread to items.count { it.conversation.unreadCount > 0 },
                            ChatFilter.Groups to items.count { it.conversation.isGroup }
                        )
                    )
                }
                if (shown.isEmpty()) {
                    item {
                        Text(stringResource(R.string.chat_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                items(shown, key = { it.conversation.id }) { item ->
                    val conv = item.conversation
                    if (conv.isGroup) {
                        SwipeableConvRow(
                            avatarUrl = null,
                            isGroup = true,
                            name = conv.name ?: stringResource(R.string.w_group),
                            lastMessage = conv.lastMessage ?: "",
                            unread = conv.unreadCount,
                            date = chatListDate(conv.lastAtMillis),
                            onClick = { onOpenGroup(conv.id) },
                            onDelete = { viewModel.deleteConversation(conv.id) },
                            onMarkUnread = { viewModel.markUnread(conv.id) }
                        )
                    } else {
                        val other = item.otherProfile
                        val otherUid = conv.participants.firstOrNull { it != myUid } ?: return@items
                        SwipeableConvRow(
                            avatarUrl = other?.photoUrl,
                            isGroup = false,
                            name = other?.username ?: other?.displayName ?: otherUid.take(6),
                            lastMessage = conv.lastMessage ?: "",
                            unread = conv.unreadCount,
                            date = chatListDate(conv.lastAtMillis),
                            onClick = { onOpenChat(otherUid) },
                            onDelete = { viewModel.deleteConversation(conv.id) },
                            onMarkUnread = { viewModel.markUnread(conv.id) }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** Diálogo para iniciar un chat nuevo: busca entre tus seguidores/seguidos. */
@Composable
private fun NewChatDialog(
    contacts: List<PublicProfile>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = contacts.filter {
        val q = query.trim().lowercase()
        q.isEmpty() || (it.username ?: "").lowercase().contains(q) ||
            (it.displayName ?: "").lowercase().contains(q)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.chat_list_screen_v2_nuevo_mensaje), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.size(8.dp))
            val closeKeyboard = com.meteomontana.android.ui.components.rememberKeyboardDismisser()
            com.meteomontana.android.ui.components.CumbreSearchField(query, { query = it },
                stringResource(R.string.chat_list_screen_v2_buscar_entre_seguidores_seguidos), onSearch = { closeKeyboard() })
            Spacer(Modifier.size(8.dp))
            if (contacts.isEmpty()) {
                Text(stringResource(R.string.chat_list_screen_v2_sigue_a_alguien_o_que),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp).dismissKeyboardOnScroll()) {
                    items(filtered, key = { it.uid }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { closeKeyboard(); onPick(p.uid) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (p.photoUrl != null) {
                                AsyncImage(model = p.photoUrl, contentDescription = null,
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                            } else {
                                Box(Modifier.size(36.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant))
                            }
                            Text("@" + (p.username ?: p.displayName ?: p.uid.take(6)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
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
    onMarkUnread: () -> Unit,
    isGroup: Boolean = false,
    date: String = ""
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
                    icon = Icons.Outlined.Delete, label = stringResource(R.string.chat_list_screen_v2_borrar),
                    alignment = Alignment.CenterEnd,
                    tint = MaterialTheme.colorScheme.onErrorContainer)
                SwipeToDismissBoxValue.StartToEnd -> SwipeBg(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    icon = Icons.Outlined.Email, label = stringResource(R.string.chat_list_screen_no_leido),
                    alignment = Alignment.CenterStart,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                else -> {}
            }
        }
    ) {
        ConvRow(avatarUrl = avatarUrl, name = name, lastMessage = lastMessage,
            unread = unread, onClick = onClick, isGroup = isGroup, date = date)
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
    onClick: () -> Unit,
    isGroup: Boolean = false,
    date: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isGroup) {
            Box(Modifier.size(44.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Group, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
            }
        } else if (avatarUrl != null) {
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
            Text(if (isGroup) name else "@$name", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text(lastMessage, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
        }
        // Fecha arriba y, debajo, el contador de no leídos (como en iOS).
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (date.isNotEmpty()) {
                Text(date, fontFamily = com.meteomontana.android.ui.theme.Mono, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (unread > 0) {
                Box(modifier = Modifier.size(22.dp).clip(CircleShape)
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
}

/** Filtros de la lista de mensajes. */
private enum class ChatFilter(val label: Int) {
    All(R.string.chat_filter_all), Unread(R.string.chat_filter_unread), Groups(R.string.chat_filter_groups)
}

/** Buscador en píldora + filtros con su recuento, igual que el de Escuelas. Espejo de ChatListView.searchBar (iOS). */
@Composable
private fun ChatSearchBar(
    query: String, onQuery: (String) -> Unit,
    filter: ChatFilter, onFilter: (ChatFilter) -> Unit,
    counts: Map<ChatFilter, Int>
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.chat_search_hint), style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = CircleShape,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatFilter.entries.forEach { f ->
                val on = f == filter
                Row(
                    Modifier.clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { onFilter(f) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val fg = if (on) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onBackground
                    Text(stringResource(f.label), color = fg, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text("${counts[f] ?: 0}", color = fg.copy(alpha = 0.8f), fontSize = 10.sp,
                        fontFamily = com.meteomontana.android.ui.theme.Mono)
                }
            }
        }
    }
}

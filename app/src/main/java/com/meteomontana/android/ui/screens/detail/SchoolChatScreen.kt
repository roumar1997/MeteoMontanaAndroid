@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.port.SchoolChatService
import com.meteomontana.android.domain.usecase.presence.GetSchoolPresenceUseCase
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SchoolChatViewModel @Inject constructor(
    private val chatService: SchoolChatService,
    private val getPublicProfile: GetPublicProfileUseCase,
    private val getPresence: GetSchoolPresenceUseCase
) : ViewModel() {
    var messages by mutableStateOf<List<SchoolChatService.Message>>(emptyList())
        private set
    var presentList by mutableStateOf<List<SchoolPresence>>(emptyList())
        private set
    var text by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set
    private var memberNames = mutableMapOf<String, String>()
    var namesVersion by mutableStateOf(0)
        private set

    val presentNow: Set<String> get() = presentList.map { it.uid }.toSet()

    private var started = false

    fun start(schoolId: String, myUid: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            chatService.observeMessages(schoolId, 100).collect { msgs ->
                resolveNames(msgs.map { it.fromUid }, myUid)
                messages = msgs
            }
        }
        refreshPresence(schoolId)
    }

    // Deslizar hacia abajo para pedir otra vez quién hay — los mensajes ya
    // llegan en vivo por el listener de arriba, pero la presencia no tiene
    // uno propio (Álvaro, 2026-09-04: mejor a demanda que sondear sola).
    fun refreshPresence(schoolId: String) {
        viewModelScope.launch {
            try {
                presentList = getPresence.execute(schoolId)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun resolveNames(uids: List<String>, myUid: String) {
        uids.toSet().forEach { uid ->
            if (uid != myUid && memberNames[uid] == null) {
                try {
                    val p = getPublicProfile.invoke(uid)
                    memberNames[uid] = p.username ?: p.displayName ?: uid.take(6)
                    namesVersion++
                } catch (_: Exception) {
                }
            }
        }
    }

    fun nameFor(uid: String, myUid: String): String =
        if (uid == myUid) "Tú" else (memberNames[uid] ?: uid.take(6))

    fun send(schoolId: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || sending) return
        sending = true
        text = ""
        viewModelScope.launch {
            try {
                chatService.sendMessage(schoolId, trimmed)
            } catch (_: Exception) {
            }
            sending = false
        }
    }
}

/**
 * Chat ABIERTO de una escuela: cualquiera lo lee y escribe, sin "unirse".
 * Cabecera fusiona presencia + chat, igual que `SchoolChatView.swift`.
 */
@Composable
fun SchoolChatScreen(
    schoolId: String,
    schoolName: String,
    myUid: String,
    onBack: () -> Unit,
    onOpenChat: (uid: String, name: String) -> Unit,
    // Espacio ya reservado abajo por el host (cápsula de tabs + navbar). Se
    // descuenta del imePadding para que el campo quede PEGADO al teclado, no
    // flotando esa altura por encima — mismo motivo que ChatScreen.kt.
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: SchoolChatViewModel = hiltViewModel()
) {
    var showAllPresent by remember { mutableStateOf(false) }
    LaunchedEffect(schoolId) { viewModel.start(schoolId, myUid) }
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) listState.animateScrollToItem(viewModel.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .consumeWindowInsets(androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset))
        .imePadding()
    ) {
        // Cabecera
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Atrás",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    schoolName,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "se borra solo pasados unos días",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (viewModel.presentList.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clickable { showAllPresent = true }
                        .padding(start = Spacing.md, top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier
                        .clip(CircleShape)
                        .background(Terra)
                        .size(7.dp))
                    Spacer(Modifier.padding(start = Spacing.xs))
                    Text(
                        "${viewModel.presentList.size} aquí ahora",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Terra
                    )
                    Text(
                        " · ver todos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

        var isRefreshing by remember { mutableStateOf(false) }
        val refreshScope = androidx.compose.runtime.rememberCoroutineScope()
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refreshPresence(schoolId)
                refreshScope.launch {
                    kotlinx.coroutines.delay(600)
                    isRefreshing = false
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)
        ) {
            items(viewModel.messages) { m ->
                MessageBubble(
                    message = m,
                    isMine = m.fromUid == myUid,
                    isPresent = viewModel.presentNow.contains(m.fromUid),
                    name = viewModel.nameFor(m.fromUid, myUid),
                    onClick = {
                        if (m.fromUid != myUid && viewModel.presentNow.contains(m.fromUid)) {
                            onOpenChat(m.fromUid, viewModel.nameFor(m.fromUid, myUid))
                        }
                    }
                )
                Spacer(Modifier.padding(top = Spacing.xs))
            }
        }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = viewModel.text,
                onValueChange = { viewModel.text = it },
                placeholder = { Text("Escribe algo...", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                singleLine = true
            )
            Spacer(Modifier.padding(start = Spacing.xs))
            IconButton(
                onClick = { viewModel.send(schoolId) },
                enabled = viewModel.text.trim().isNotEmpty() && !viewModel.sending
            ) {
                Icon(
                    Icons.Outlined.Send,
                    contentDescription = "Enviar",
                    tint = if (viewModel.text.trim().isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Terra
                )
            }
        }
    }

    if (showAllPresent) {
        Dialog(onDismissRequest = { showAllPresent = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${viewModel.presentList.size} aquí ahora",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Cerrar", style = MaterialTheme.typography.bodyMedium, color = Terra,
                            modifier = Modifier.clickable { showAllPresent = false })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                        items(viewModel.presentList) { person ->
                            val isMe = person.uid == myUid
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isMe) {
                                        showAllPresent = false
                                        onOpenChat(person.uid, person.displayName ?: person.username ?: "Usuario")
                                    }
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (person.photoUrl != null) {
                                    AsyncImage(model = person.photoUrl, contentDescription = null,
                                        modifier = Modifier.size(36.dp).clip(CircleShape))
                                } else {
                                    Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant))
                                }
                                Spacer(Modifier.padding(start = Spacing.sm))
                                Text(
                                    if (isMe) "Tú" else (person.displayName ?: person.username ?: "Usuario"),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!isMe) {
                                    Icon(Icons.Outlined.Chat, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: SchoolChatService.Message,
    isMine: Boolean,
    isPresent: Boolean,
    name: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) androidx.compose.foundation.layout.Arrangement.End
                                 else androidx.compose.foundation.layout.Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clickable(enabled = !isMine && isPresent, onClick = onClick)
        ) {
            if (!isMine) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Terra
                    )
                    if (isPresent) {
                        Spacer(Modifier.padding(start = 6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Terra)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("AQUÍ AHORA", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.padding(top = 2.dp))
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isMine) Terra else MaterialTheme.colorScheme.surface)
                    .then(
                        if (!isMine) Modifier
                        else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

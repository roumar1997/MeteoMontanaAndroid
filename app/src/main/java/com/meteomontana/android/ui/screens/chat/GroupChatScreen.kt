package com.meteomontana.android.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.KtorChatPushApi
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class GroupChatUiState(
    val convId: String,
    val name: String = "Grupo",
    val messages: List<ChatService.ChatMessage> = emptyList(),
    /** uid -> nombre para mostrar, para etiquetar quién escribe cada mensaje. */
    val memberNames: Map<String, String> = emptyMap(),
    val myUid: String = "",
    val canWrite: Boolean = false,
    val loading: Boolean = true,
    val replyingTo: ChatService.ChatMessage? = null
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatService: ChatService,
    private val authService: AuthService,
    private val getPublicProfile: GetPublicProfileUseCase,
    private val chatPushApi: KtorChatPushApi
) : ViewModel() {
    private val convId: String = checkNotNull(savedStateHandle["convId"])
    private val me: String = authService.currentUid() ?: ""

    private val _state = MutableStateFlow(GroupChatUiState(convId = convId, myUid = me))
    val state: StateFlow<GroupChatUiState> = _state.asStateFlow()

    private val nameCache = mutableMapOf<String, String>()

    init {
        // Datos del grupo (nombre, participantes) + si "borré para mí".
        viewModelScope.launch {
            chatService.observeMyConversations().collect { convs ->
                val conv = convs.firstOrNull { it.id == convId } ?: return@collect
                resolveNames(conv.participants)
                _state.value = _state.value.copy(
                    name = conv.name ?: "Grupo",
                    canWrite = me in conv.participants,
                    memberNames = nameCache.toMap()
                )
            }
        }
        // Mensajes del grupo.
        viewModelScope.launch {
            chatService.observeMessages(convId).collect { msgs ->
                resolveNames(msgs.map { it.fromUid })
                _state.value = _state.value.copy(
                    messages = msgs, memberNames = nameCache.toMap(), loading = false
                )
                runCatching { chatService.markRead(convId) }
            }
        }
    }

    private suspend fun resolveNames(uids: List<String>) {
        uids.distinct().filter { it != me && it !in nameCache }.forEach { uid ->
            val p = runCatching { getPublicProfile(uid) }.getOrNull()
            nameCache[uid] = p?.username ?: p?.displayName ?: uid.take(6)
        }
    }

    fun startReply(msg: ChatService.ChatMessage) { _state.value = _state.value.copy(replyingTo = msg) }
    fun cancelReply() { _state.value = _state.value.copy(replyingTo = null) }

    fun send(text: String) {
        if (!_state.value.canWrite || text.isBlank()) return
        val reply = _state.value.replyingTo
        _state.value = _state.value.copy(replyingTo = null)
        viewModelScope.launch {
            val ok = runCatching {
                chatService.sendGroupMessage(
                    convId, text,
                    replyToId = reply?.id, replyText = reply?.text, replyFromUid = reply?.fromUid
                )
            }.isSuccess
            if (ok) runCatching { chatPushApi.notifyGroup(convId, text) }
        }
    }
}

@Composable
fun GroupChatScreen(
    onBack: () -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            kotlinx.coroutines.delay(50)
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()
        .imePadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Column {
                Text(state.name, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("${state.memberNames.size + 1} miembros",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                GroupMessageBubble(
                    msg = msg,
                    myUid = state.myUid,
                    senderName = state.memberNames[msg.fromUid] ?: "",
                    nameFor = { uid -> if (uid == state.myUid) "Tú" else state.memberNames[uid] ?: "" },
                    onReply = { viewModel.startReply(msg) }
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (!state.canWrite) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Ya no eres miembro de este grupo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            state.replyingTo?.let { reply ->
                val who = if (reply.fromUid == state.myUid) "Tú" else (state.memberNames[reply.fromUid] ?: "")
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(3.dp).height(34.dp).background(MaterialTheme.colorScheme.primary))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(if (who.isNotBlank()) "Respondiendo a $who" else "Respondiendo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(reply.text, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { viewModel.cancelReply() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancelar respuesta",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            Row(modifier = Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje",
                        style = MaterialTheme.typography.bodyMedium) },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
                IconButton(
                    onClick = { if (text.isNotBlank()) { viewModel.send(text); text = "" } },
                    enabled = text.isNotBlank()
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = "Enviar",
                        tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(
    msg: ChatService.ChatMessage,
    myUid: String,
    senderName: String,
    nameFor: (String) -> String,
    onReply: (ChatService.ChatMessage) -> Unit
) {
    val isMine = msg.fromUid == myUid
    val bg = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface

    val density = LocalDensity.current
    val thresholdPx = with(density) { 56.dp.toPx() }
    val offsetX = remember(msg.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var triggered by remember(msg.id) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().pointerInput(msg.id) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (offsetX.value >= thresholdPx && !triggered) { triggered = true; onReply(msg) }
                scope.launch { offsetX.animateTo(0f) }
            },
            onDragCancel = { scope.launch { offsetX.animateTo(0f) } }
        ) { _, dragAmount ->
            val next = (offsetX.value + dragAmount).coerceIn(0f, thresholdPx * 1.4f)
            scope.launch { offsetX.snapTo(next) }
            if (next < thresholdPx) triggered = false
        }
    }) {
        Icon(Icons.Outlined.Reply, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
        Row(modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetX.value.roundToInt(), 0) },
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
            Column(modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Nombre del emisor (solo en mensajes de otros).
                if (!isMine && senderName.isNotBlank()) {
                    Text(senderName, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                val replyText = msg.replyText
                if (msg.replyToId != null && replyText != null) {
                    val who = nameFor(msg.replyFromUid ?: "")
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(fg.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Box(Modifier.width(3.dp).height(28.dp).background(fg.copy(alpha = 0.5f)))
                        Column(Modifier.padding(start = 6.dp)) {
                            if (who.isNotBlank()) {
                                Text(who, style = MaterialTheme.typography.labelMedium,
                                    color = fg.copy(alpha = 0.9f))
                            }
                            Text(replyText, style = MaterialTheme.typography.bodySmall,
                                color = fg.copy(alpha = 0.8f), maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(msg.text, color = fg, style = MaterialTheme.typography.bodyMedium)
                msg.createdAtMillis?.let {
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(it))
                    Text(time, color = fg.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

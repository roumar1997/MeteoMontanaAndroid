@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.ui.components.VerifiedCheck

import com.meteomontana.android.ui.components.userRing

import com.meteomontana.android.util.AppText
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
import androidx.compose.material.icons.outlined.LocationOn
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
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.screens.chat.ChatDayDivider
import com.meteomontana.android.ui.screens.chat.ChatInputPill
import com.meteomontana.android.ui.screens.chat.ChatLockedBar
import com.meteomontana.android.ui.screens.chat.chatDayStartIds
import com.meteomontana.android.ui.screens.chat.chatBubbleSurface
import com.meteomontana.android.ui.screens.chat.ChatEmptyState
import com.meteomontana.android.ui.screens.chat.ChatTopBar
import com.meteomontana.android.ui.screens.chat.ChatSubtitle
import com.meteomontana.android.ui.screens.chat.ChatInitialAvatar
import com.meteomontana.android.ui.screens.chat.ChatSenderAvatarSlot
import com.meteomontana.android.ui.screens.chat.chatRunPositions
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.width

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
    /** false hasta la primera respuesta de presencia: evita enseñar «Pulsa Aquí» antes de saber si ya estás marcado. */
    var presenceLoaded by mutableStateOf(false)
        private set
    private var memberNames = mutableMapOf<String, String>()
    private val memberPhotos = mutableMapOf<String, String>()
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
                presenceLoaded = true
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
                    p.photoUrl?.let { memberPhotos[uid] = it }
                    namesVersion++
                } catch (_: Exception) {
                }
            }
        }
    }

    fun photoFor(uid: String): String? = memberPhotos[uid]

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
    onOpenProfile: (uid: String) -> Unit = {},
    // Espacio ya reservado abajo por el host (cápsula de tabs + navbar). Se
    // descuenta del imePadding para que el campo quede PEGADO al teclado, no
    // flotando esa altura por encima — mismo motivo que ChatScreen.kt.
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: SchoolChatViewModel = hiltViewModel()
) {
    var showAllPresent by remember { mutableStateOf(false) }
    // Mismo ViewModel que usan los botones «Aquí»/«Ya no»: reacciona al instante al marcarte o quitarte.
    val presenceVm: com.meteomontana.android.ui.components.SchoolPresenceViewModel = hiltViewModel()
    LaunchedEffect(schoolId, myUid) { presenceVm.load(schoolId, myUid) }
    val notMarkedHere = presenceVm.loaded && !presenceVm.iAmHere
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
        ChatTopBar(
            title = schoolName, onBack = onBack,
            avatar = { ChatInitialAvatar(schoolName, com.meteomontana.android.ui.theme.Ok) },
            subtitle = { ChatSubtitle(stringResource(R.string.school_chat_screen_v2_se_borra_solo_pasados_unos)) },
            actions = {
                com.meteomontana.android.ui.components.PresenceMarkControl(
                    schoolId = schoolId, myUid = myUid,
                    onChanged = { viewModel.refreshPresence(schoolId) },
                    visibleWhenHere = true
                )
            }
        )
        if (viewModel.presentList.isNotEmpty() && !notMarkedHere) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAllPresent = true }
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clip(CircleShape).background(Terra).size(7.dp))
                Spacer(Modifier.padding(start = Spacing.xs))
                Text(
                    stringResource(R.string.school_chat_screen_v2_1_s_aqui_ahora_2, viewModel.presentList.size),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Terra,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.school_chat_screen_v2_ver_todos),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        }

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
        if (notMarkedHere && viewModel.messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MarkHereNotice(compact = false) }
        } else if (viewModel.messages.isEmpty()) ChatEmptyState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)
        ) {
            if (notMarkedHere && viewModel.messages.isNotEmpty()) item { MarkHereNotice(compact = true) }
            val dayStarts = chatDayStartIds(viewModel.messages.map { it.id to it.createdAtMillis })
            val msgs = viewModel.messages
            val runs = chatRunPositions(msgs.map { it.fromUid }) { msgs[it].id in dayStarts }
            itemsIndexed(msgs) { i, m ->
                @Suppress("UNUSED_EXPRESSION") viewModel.namesVersion
                dayStarts[m.id]?.let { ChatDayDivider(it) }
                MessageBubble(
                    showName = runs[i].first, showAvatar = runs[i].last,
                    photoUrl = viewModel.photoFor(m.fromUid), onOpenProfile = { onOpenProfile(m.fromUid) },
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
        // Sin marcar «Aquí» no se puede escribir: la barra con candado ocupa el sitio del campo.
        if (notMarkedHere) ChatLockedBar(onHere = {
            com.meteomontana.android.ui.components.PresenceMarkControl(
                schoolId = schoolId, myUid = myUid,
                onChanged = { viewModel.refreshPresence(schoolId) },
                visibleWhenHere = false
            )
        })
        else ChatInputPill(
            text = viewModel.text, onTextChange = { viewModel.text = it }, onSend = { viewModel.send(schoolId) },
            enabled = !viewModel.sending, placeholder = stringResource(R.string.school_chat_screen_v2_escribe_algo)
        )
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
                            stringResource(R.string.school_chat_screen_v2_1_s_aqui_ahora, viewModel.presentList.size),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(stringResource(R.string.school_chat_screen_v2_cerrar), style = MaterialTheme.typography.bodyMedium, color = Terra,
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
                                        onOpenChat(person.uid, person.displayName ?: person.username ?: AppText.get(com.meteomontana.android.feature.chat.R.string.w_user))
                                    }
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (person.photoUrl != null) {
                                    AsyncImage(model = person.photoUrl, contentDescription = null,
                                        modifier = Modifier.userRing(person.uid, 36.dp).size(36.dp).clip(CircleShape))
                                } else {
                                    Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant))
                                }
                                Spacer(Modifier.padding(start = Spacing.sm))
                                Text(
                                    if (isMe) stringResource(com.meteomontana.android.feature.chat.R.string.school_presence_row_v3_tu) else (person.displayName ?: person.username ?: "Usuario"),
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

/** Aviso cuando el usuario no ha marcado «Aquí»: el chat y el recuento de gente dependen de esa marca. */
@Composable
private fun MarkHereNotice(compact: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Terra,
            modifier = Modifier.size(if (compact) 22.dp else 40.dp))
        Text(
            stringResource(R.string.school_chat_mark_here_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
internal fun MessageBubble(
    showName: Boolean,
    showAvatar: Boolean,
    photoUrl: String?,
    onOpenProfile: () -> Unit,
    message: SchoolChatService.Message,
    isMine: Boolean,
    isPresent: Boolean,
    name: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) androidx.compose.foundation.layout.Arrangement.End
                                 else androidx.compose.foundation.layout.Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            ChatSenderAvatarSlot(showAvatar, photoUrl, name, message.fromUid, onOpenProfile)
            Spacer(Modifier.width(6.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 260.dp)
                .clickable(enabled = !isMine && isPresent, onClick = onClick)
        ) {
            if (!isMine && showName) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isPresent) name + " · " + stringResource(R.string.chat_here_tag) else name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Terra
                    )
                }
                Spacer(Modifier.padding(top = 2.dp))
            }
            Column(
                modifier = Modifier
                    .chatBubbleSurface(isMine)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onBackground
                )
                message.createdAtMillis?.let {
                    Text(
                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = (if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.75f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

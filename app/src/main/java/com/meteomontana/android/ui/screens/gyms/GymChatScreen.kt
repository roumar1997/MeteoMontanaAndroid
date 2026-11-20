package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.itemsIndexed
import com.meteomontana.android.ui.screens.chat.chatRunPositions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.port.SchoolChatService
import com.meteomontana.android.domain.usecase.gym.ClearGymPresenceUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymPresenceUseCase
import com.meteomontana.android.domain.usecase.gym.MarkGymPresenceUseCase
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import com.meteomontana.android.ui.components.PresencePrivacySheet
import com.meteomontana.android.ui.screens.chat.ChatDayDivider
import com.meteomontana.android.ui.screens.chat.ChatInitialAvatar
import com.meteomontana.android.ui.screens.chat.ChatInputPill
import com.meteomontana.android.ui.screens.chat.ChatSubtitle
import com.meteomontana.android.ui.screens.chat.ChatTopBar
import com.meteomontana.android.ui.screens.chat.ChatLockedBar
import com.meteomontana.android.ui.screens.chat.chatDayStartIds
import com.meteomontana.android.ui.screens.detail.MessageBubble
import com.meteomontana.android.ui.theme.Ok
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.terraFillColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Id del chat de un rocódromo dentro de `school_chats` (mismas reglas de Firestore que las escuelas). */
internal fun gymChatId(gymId: Long) = "gym-$gymId"

/**
 * Chat de un rocódromo: el mismo chat abierto que el de escuela (`school_chats/{id}/messages`, ya en las reglas de
 * Firestore) con la clave `gym-{id}`. Como en las escuelas, solo escribe quien ha marcado «Aquí» (lo mira la app; las
 * reglas no lo exigen, igual que en escuelas).
 */
@HiltViewModel
class GymChatViewModel @Inject constructor(
    private val chatService: SchoolChatService,
    private val getPublicProfile: GetPublicProfileUseCase,
    private val getPresence: GetGymPresenceUseCase,
    private val markPresence: MarkGymPresenceUseCase,
    private val clearPresence: ClearGymPresenceUseCase
) : ViewModel() {
    var messages by mutableStateOf<List<SchoolChatService.Message>>(emptyList())
        private set
    var text by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set
    var namesVersion by mutableStateOf(0)
        private set
    var people by mutableStateOf<List<SchoolPresence>>(emptyList())
        private set
    /** false hasta la primera respuesta de presencia: no se enseña el candado antes de saber si ya estás «Aquí». */
    var presenceLoaded by mutableStateOf(false)
        private set
    var iAmHere by mutableStateOf(false)
        private set
    var presenceBusy by mutableStateOf(false)
        private set
    private val names = mutableMapOf<String, String>()
    private val photos = mutableMapOf<String, String>()
    private var started = false

    fun start(gymId: Long, myUid: String) {
        if (started) return
        started = true
        loadPresence(gymId, myUid)
        viewModelScope.launch {
            chatService.observeMessages(gymChatId(gymId), 100).collect { msgs ->
                msgs.map { it.fromUid }.toSet().filter { it != myUid && it !in names }.forEach { uid ->
                    runCatching { getPublicProfile.invoke(uid) }.onSuccess {
                        names[uid] = it.username ?: it.displayName ?: uid.take(6)
                        it.photoUrl?.let { url -> photos[uid] = url }
                        namesVersion++
                    }
                }
                messages = msgs
            }
        }
    }

    fun loadPresence(gymId: Long, myUid: String) {
        viewModelScope.launch {
            runCatching { getPresence.execute(gymId) }.onSuccess { list ->
                people = list
                iAmHere = list.any { it.uid == myUid }
                presenceLoaded = true
            }
        }
    }

    fun markHere(gymId: Long, myUid: String) {
        if (presenceBusy) return
        presenceBusy = true
        viewModelScope.launch {
            val ok = runCatching { markPresence.execute(gymId) }.isSuccess
            presenceBusy = false
            if (ok) iAmHere = true
            loadPresence(gymId, myUid)
        }
    }

    fun leave(gymId: Long, myUid: String) {
        if (presenceBusy) return
        presenceBusy = true
        viewModelScope.launch {
            val ok = runCatching { clearPresence.execute(gymId) }.isSuccess
            presenceBusy = false
            if (ok) iAmHere = false
            loadPresence(gymId, myUid)
        }
    }

    fun photoFor(uid: String): String? = photos[uid]

    fun nameFor(uid: String, myUid: String): String = if (uid == myUid) "Tú" else (names[uid] ?: uid.take(6))

    fun send(gymId: Long) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || sending || !iAmHere) return
        sending = true
        text = ""
        viewModelScope.launch {
            runCatching { chatService.sendMessage(gymChatId(gymId), trimmed.take(500)) }
            sending = false
        }
    }
}

@Composable
fun GymChatScreen(
    gymId: Long,
    gymName: String,
    myUid: String,
    onBack: () -> Unit,
    onOpenChat: (uid: String) -> Unit,
    onOpenProfile: (uid: String) -> Unit = {},
    bottomInset: Dp = 0.dp,
    viewModel: GymChatViewModel = hiltViewModel()
) {
    LaunchedEffect(gymId) { viewModel.start(gymId, myUid) }
    val listState = rememberLazyListState()
    var privacyOpen by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) listState.animateScrollToItem(viewModel.messages.size - 1)
    }
    val locked = viewModel.presenceLoaded && !viewModel.iAmHere
    val presentNow = viewModel.people.map { it.uid }.toSet()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .consumeWindowInsets(PaddingValues(bottom = bottomInset)).imePadding()
    ) {
        ChatTopBar(
            title = gymName, onBack = onBack,
            avatar = { ChatInitialAvatar(gymName, Ok) },
            subtitle = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (viewModel.people.isNotEmpty()) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Ok))
                        Spacer(Modifier.size(5.dp))
                    }
                    ChatSubtitle(
                        if (viewModel.people.isEmpty()) stringResource(R.string.school_chat_screen_v2_se_borra_solo_pasados_unos)
                        else stringResource(R.string.gym_chat_here_now, viewModel.people.size)
                    )
                }
            }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (viewModel.messages.isEmpty()) Text(
                stringResource(R.string.gym_chat_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(Spacing.xl)
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.md)) {
                val dayStarts = chatDayStartIds(viewModel.messages.map { it.id to it.createdAtMillis })
                val msgs = viewModel.messages
                val runs = chatRunPositions(msgs.map { it.fromUid }) { msgs[it].id in dayStarts }
                itemsIndexed(msgs, key = { _, m -> m.id }) { i, m ->
                    @Suppress("UNUSED_EXPRESSION") viewModel.namesVersion
                    dayStarts[m.id]?.let { ChatDayDivider(it) }
                    MessageBubble(
                        showName = runs[i].first, showAvatar = runs[i].last,
                        photoUrl = viewModel.photoFor(m.fromUid), onOpenProfile = { onOpenProfile(m.fromUid) },
                        message = m, isMine = m.fromUid == myUid, isPresent = m.fromUid in presentNow,
                        name = viewModel.nameFor(m.fromUid, myUid), onClick = { onOpenChat(m.fromUid) }
                    )
                    Spacer(Modifier.padding(top = Spacing.xs))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        if (locked) ChatLockedBar(onHere = {
            Text(
                stringResource(R.string.gym_here_top).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                modifier = Modifier.clip(CircleShape).background(terraFillColor()).clickable(enabled = !viewModel.presenceBusy) { privacyOpen = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }) else ChatInputPill(
            text = viewModel.text, onTextChange = { viewModel.text = it }, onSend = { viewModel.send(gymId) },
            enabled = !viewModel.sending, placeholder = stringResource(R.string.school_chat_screen_v2_escribe_algo)
        )
    }
    if (privacyOpen) PresencePrivacySheet(
        onConfirm = { privacyOpen = false; viewModel.markHere(gymId, myUid) }, onDismiss = { privacyOpen = false }
    )
}

package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.R
import com.meteomontana.android.domain.port.SchoolChatService
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import com.meteomontana.android.ui.screens.detail.MessageBubble
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Id del chat de un rocódromo dentro de `school_chats` (mismas reglas de Firestore que las escuelas). */
internal fun gymChatId(gymId: Long) = "gym-$gymId"

/**
 * Chat de un rocódromo: el mismo chat abierto que el de escuela (`school_chats/{id}/messages`, ya en las reglas de
 * Firestore) con la clave `gym-{id}`. Sin servidor nuevo. No exige «Aquí» (los rocódromos no lo piden).
 */
@HiltViewModel
class GymChatViewModel @Inject constructor(
    private val chatService: SchoolChatService,
    private val getPublicProfile: GetPublicProfileUseCase
) : ViewModel() {
    var messages by mutableStateOf<List<SchoolChatService.Message>>(emptyList())
        private set
    var text by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set
    var namesVersion by mutableStateOf(0)
        private set
    private val names = mutableMapOf<String, String>()
    private var started = false

    fun start(gymId: Long, myUid: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            chatService.observeMessages(gymChatId(gymId), 100).collect { msgs ->
                msgs.map { it.fromUid }.toSet().filter { it != myUid && it !in names }.forEach { uid ->
                    runCatching { getPublicProfile.invoke(uid) }.onSuccess {
                        names[uid] = it.username ?: it.displayName ?: uid.take(6); namesVersion++
                    }
                }
                messages = msgs
            }
        }
    }

    fun nameFor(uid: String, myUid: String): String = if (uid == myUid) "Tú" else (names[uid] ?: uid.take(6))

    fun send(gymId: Long) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || sending) return
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
    bottomInset: Dp = 0.dp,
    viewModel: GymChatViewModel = hiltViewModel()
) {
    LaunchedEffect(gymId) { viewModel.start(gymId, myUid) }
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) listState.animateScrollToItem(viewModel.messages.size - 1)
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .consumeWindowInsets(androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset)).imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back), tint = MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.weight(1f)) {
                Text(gymName, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold), maxLines = 2)
                Text(
                    stringResource(R.string.school_chat_screen_v2_se_borra_solo_pasados_unos),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (viewModel.messages.isEmpty()) Text(
                stringResource(R.string.gym_chat_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(Spacing.xl)
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
                items(viewModel.messages, key = { it.id }) { m ->
                    @Suppress("UNUSED_EXPRESSION") viewModel.namesVersion
                    MessageBubble(
                        message = m, isMine = m.fromUid == myUid, isPresent = true,
                        name = viewModel.nameFor(m.fromUid, myUid), onClick = { onOpenChat(m.fromUid) }
                    )
                    Spacer(Modifier.padding(top = Spacing.xs))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = viewModel.text, onValueChange = { viewModel.text = it },
                placeholder = { Text(stringResource(R.string.school_chat_screen_v2_escribe_algo), style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(50), singleLine = true,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            IconButton(onClick = { viewModel.send(gymId) }, enabled = viewModel.text.trim().isNotEmpty() && !viewModel.sending) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send, stringResource(R.string.school_chat_screen_v2_enviar),
                    tint = if (viewModel.text.trim().isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Terra
                )
            }
        }
    }
}

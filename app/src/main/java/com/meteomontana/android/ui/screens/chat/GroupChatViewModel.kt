package com.meteomontana.android.ui.screens.chat

import com.meteomontana.android.util.AppText
import androidx.compose.animation.core.Animatable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Send
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.meteomontana.android.domain.port.AuthService
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.domain.usecase.meetups.GetMeetupByConversationUseCase
import com.meteomontana.android.domain.usecase.meetups.UpdateMyGearUseCase
import com.meteomontana.android.domain.model.MeetupMember
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.meteomontana.android.ui.screens.meetups.totalGearSummary
import com.meteomontana.android.ui.screens.meetups.gearSummaryText
import com.meteomontana.android.ui.screens.meetups.MemberAvatar
import com.meteomontana.android.ui.screens.meetups.EditGearDialog
import com.meteomontana.android.ui.screens.meetups.gearItemsForDiscipline
import com.meteomontana.android.ui.screens.meetups.parseGear
import com.meteomontana.android.R
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import com.meteomontana.android.push.MutedChatsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

// Estado y ViewModel del chat de grupo (separados de la pantalla).

data class GroupChatUiState(
    val convId: String,
    val name: String = AppText.get(R.string.w_group),
    val messages: List<ChatService.ChatMessage> = emptyList(),
    /** uid -> nombre para mostrar, para etiquetar quién escribe cada mensaje. */
    val memberNames: Map<String, String> = emptyMap(),
    val myUid: String = "",
    val canWrite: Boolean = false,
    val loading: Boolean = true,
    /** true si la ventana en vivo llegó llena → hay mensajes anteriores por cargar. */
    val canLoadMore: Boolean = false,
    val replyingTo: ChatService.ChatMessage? = null,
    // Datos de la quedada asociada (si esta conversación es de una quedada).
    val meetupId: String? = null,
    val schoolLat: Double? = null,
    val schoolLon: Double? = null,
    val schoolName: String? = null,
    val muted: Boolean = false,
    val gearSummary: String? = null,
    val members: List<MeetupMember> = emptyList(),
    val discipline: String? = null,
    val myGearJson: String? = null
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val chatService: ChatService,
    private val authService: AuthService,
    private val getPublicProfile: GetPublicProfileUseCase,
    private val getMeetupByConversation: GetMeetupByConversationUseCase,
    private val notifyGroupMessage: com.meteomontana.android.domain.usecase.chat.NotifyGroupMessageUseCase,
    private val updateMyGearUseCase: UpdateMyGearUseCase,
    private val getMeetupInviteLink: com.meteomontana.android.domain.usecase.meetups.GetMeetupInviteLinkUseCase
) : ViewModel() {
    private val convId: String = checkNotNull(savedStateHandle["convId"])
    private val me: String = authService.currentUid() ?: ""

    private val _state = MutableStateFlow(
        GroupChatUiState(convId = convId, myUid = me,
            muted = MutedChatsStore.isMuted(appContext, convId))
    )
    val state: StateFlow<GroupChatUiState> = _state.asStateFlow()

    private val nameCache = mutableMapOf<String, String>()

    // Ventana en vivo creciente (igual que el chat 1-a-1): empieza en 50 y +50 al
    // pulsar "Mensajes anteriores". Recorta las lecturas de Firestore al abrir.
    private val messageLimit = MutableStateFlow(ChatService.MESSAGE_PAGE)

    /** Carga otra página de mensajes anteriores (aumenta la ventana en vivo). */
    fun loadOlder() {
        messageLimit.value += ChatService.MESSAGE_PAGE
    }

    init {
        // Quedada asociada a esta conversación (para abrir su detalle desde el
        // título y para el botón "Cómo llegar"). Si no es de una quedada, no pasa nada.
        viewModelScope.launch {
            val meetup = getMeetupByConversation.execute(convId)
            if (meetup != null) {
                val members = meetup.members
                val gear = totalGearSummary(members)
                val myGear = members.firstOrNull { it.uid == me }?.gearJson
                _state.value = _state.value.copy(
                    meetupId = meetup.id,
                    schoolLat = meetup.schoolLat,
                    schoolLon = meetup.schoolLon,
                    schoolName = meetup.schoolName,
                    gearSummary = gear.ifEmpty { null },
                    members = members,
                    discipline = meetup.discipline,
                    myGearJson = myGear
                )
            }
        }
        // Datos del grupo (nombre, participantes) + si "borré para mí".
        viewModelScope.launch {
            chatService.observeMyConversations().collect { convs ->
                val conv = convs.firstOrNull { it.id == convId } ?: return@collect
                resolveNames(conv.participants)
                _state.value = _state.value.copy(
                    name = conv.name ?: AppText.get(R.string.w_group),
                    canWrite = me in conv.participants,
                    memberNames = nameCache.toMap()
                )
            }
        }
        // Mensajes del grupo (ventana creciente).
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            messageLimit.flatMapLatest { chatService.observeMessages(convId, it) }.collect { msgs ->
                resolveNames(msgs.map { it.fromUid })
                _state.value = _state.value.copy(
                    messages = msgs, memberNames = nameCache.toMap(), loading = false,
                    canLoadMore = msgs.size >= messageLimit.value
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

    /** Comparte el enlace de invitación al grupo (lo genera el backend). */
    fun shareInvite(context: Context, meetupId: String, groupName: String) {
        viewModelScope.launch {
            val link = runCatching { getMeetupInviteLink.execute(meetupId) }.getOrNull() ?: return@launch
            val text = context.getString(R.string.share_group_invite, groupName, link)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            runCatching {
                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_utils_v3_invitar_al_grupo)))
            }
        }
    }

    /** Silenciar / reactivar notificaciones de este grupo (solo en este móvil). */
    fun toggleMute() {
        val newMuted = !_state.value.muted
        MutedChatsStore.setMuted(appContext, convId, newMuted)
        _state.value = _state.value.copy(muted = newMuted)
    }

    fun updateMyGear(gearJson: String) {
        val meetupId = _state.value.meetupId ?: return
        viewModelScope.launch {
            try {
                val updated = updateMyGearUseCase.execute(meetupId, gearJson)
                val members = updated.members
                val gear = totalGearSummary(members)
                val myGear = members.firstOrNull { it.uid == me }?.gearJson
                _state.value = _state.value.copy(
                    gearSummary = gear.ifEmpty { null },
                    members = members,
                    myGearJson = myGear
                )
            } catch (_: Exception) { }
        }
    }

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
            if (ok) runCatching { notifyGroupMessage(convId, text) }
        }
    }
}

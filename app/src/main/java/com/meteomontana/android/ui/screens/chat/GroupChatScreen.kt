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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.NotificationsActive
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.meteomontana.android.data.api.KtorChatPushApi
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
import com.meteomontana.android.domain.usecase.social.GetPublicProfileUseCase
import com.meteomontana.android.push.MutedChatsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val chatPushApi: KtorChatPushApi,
    private val updateMyGearUseCase: UpdateMyGearUseCase
) : ViewModel() {
    private val convId: String = checkNotNull(savedStateHandle["convId"])
    private val me: String = authService.currentUid() ?: ""

    private val _state = MutableStateFlow(
        GroupChatUiState(convId = convId, myUid = me,
            muted = MutedChatsStore.isMuted(appContext, convId))
    )
    val state: StateFlow<GroupChatUiState> = _state.asStateFlow()

    private val nameCache = mutableMapOf<String, String>()

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
            if (ok) runCatching { chatPushApi.notifyGroup(convId, text) }
        }
    }
}

@Composable
fun GroupChatScreen(
    onBack: () -> Unit,
    onOpenMeetup: (String) -> Unit = {},
    viewModel: GroupChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .imePadding()
    ) {
        // ── Toolbar del chat (sin SheetHeader para evitar solapamiento) ──
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline,
                    androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                .padding(horizontal = com.meteomontana.android.ui.theme.Spacing.xs,
                    vertical = com.meteomontana.android.ui.theme.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, "Volver")
            }
            // Nombre + subtítulo (pulsable si es quedada)
            Column(
                modifier = Modifier.weight(1f)
                    .let { m -> state.meetupId?.let { id -> m.clickable { onOpenMeetup(id) } } ?: m }
                    .padding(horizontal = com.meteomontana.android.ui.theme.Spacing.xs)
            ) {
                Text(state.name, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    if (state.meetupId != null) "Ver detalles ›"
                    else "${state.memberNames.size + 1} miembros",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.meetupId != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Cómo llegar
            if (state.schoolLat != null && state.schoolLon != null) {
                IconButton(onClick = {
                    openDirections(context, state.schoolLat!!, state.schoolLon!!, state.schoolName)
                }) {
                    Icon(Icons.Outlined.Directions, "Cómo llegar",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            // Silenciar
            IconButton(onClick = { viewModel.toggleMute() }) {
                Icon(
                    if (state.muted) Icons.Outlined.NotificationsOff
                    else Icons.Outlined.NotificationsActive,
                    contentDescription = if (state.muted) "Activar" else "Silenciar",
                    tint = if (state.muted) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.primary
                )
            }
            // Cerrar
            TextButton(onClick = onBack) {
                Text("Cerrar", color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Expandable gear banner ──
        if (state.meetupId != null) {
            var gearExpanded by remember { mutableStateOf(false) }
            var showEditGear by remember { mutableStateOf(false) }
            val hasSomeGear = state.gearSummary != null

            Column(Modifier.fillMaxWidth()) {
                // Header row (always visible)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { gearExpanded = !gearExpanded }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Luggage, null,
                        modifier = Modifier.height(16.dp).width(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    if (hasSomeGear) {
                        Text(state.gearSummary!!, style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium, fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)),
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                            modifier = Modifier.weight(1f))
                    } else {
                        Text("Sin material indicado", style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f))
                        // Small inline add button for empty state
                        androidx.compose.material3.OutlinedButton(
                            onClick = { showEditGear = true },
                            shape = RoundedCornerShape(2.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("+ Anadir", style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (hasSomeGear) {
                        Icon(
                            if (gearExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            null, modifier = Modifier.height(12.dp).width(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }

                // Expanded content
                if (gearExpanded && hasSomeGear) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.members.forEach { member ->
                            val gear = gearSummaryText(member.gearJson)
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MemberAvatar(member.photoUrl, 24.dp)
                                Text(member.displayName ?: member.username ?: member.uid.take(6),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                    modifier = Modifier.weight(1f, fill = false))
                                if (gear.isNotEmpty()) {
                                    Text(gear, style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("sin material", style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = { showEditGear = true },
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, null, Modifier.height(14.dp).width(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Editar mi material", style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)))
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            if (showEditGear) {
                EditGearDialog(
                    discipline = state.discipline,
                    currentGearJson = state.myGearJson,
                    onDismiss = { showEditGear = false },
                    onSave = { json -> showEditGear = false; viewModel.updateMyGear(json) }
                )
            }
        }

        // reverseLayout: nuevo abajo siempre + historial hacia arriba natural
        // (ver nota en ChatScreen). Lista en orden inverso.
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.messages.reversed(), key = { it.id }) { msg ->
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

/** Abre Google Maps con indicaciones a las coordenadas de la escuela. */
internal fun openDirections(
    context: android.content.Context,
    lat: Double, lon: Double, label: String?
) {
    val uri = android.net.Uri.parse(
        "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon"
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }
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
        // El icono de responder asoma SOLO al deslizar (invisible en reposo).
        val replyIconAlpha = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
        if (replyIconAlpha > 0f) {
            Icon(Icons.Outlined.Reply, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
                    .alpha(replyIconAlpha))
        }
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

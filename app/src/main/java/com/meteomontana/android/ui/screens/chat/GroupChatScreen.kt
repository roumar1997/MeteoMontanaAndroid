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

@Composable
fun GroupChatScreen(
    onBack: () -> Unit,
    onOpenMeetup: (String) -> Unit = {},
    // Ver ChatScreen/FeedPostDetailScreen: descuenta lo reservado por el host
    // para pegar el campo al teclado.
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: GroupChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Mismo comportamiento que el chat 1-a-1: `reverseLayout` ancla lo último
    // abajo al ABRIR, pero no mueve nada después, así que al escribir tu propio
    // mensaje se quedaba fuera de la vista. Al mío se baja siempre; al ajeno,
    // solo si ya estabas abajo — si no, te sacaría de donde estás leyendo cada
    // vez que alguien del grupo escribe, que en un grupo es constante.
    val ultimo = state.messages.lastOrNull()
    LaunchedEffect(ultimo?.id) {
        if (ultimo == null) return@LaunchedEffect
        if (ultimo.fromUid == state.myUid || listState.firstVisibleItemIndex <= 1) {
            // Desde muy arriba se salta sin animar (animar cientos de mensajes atasca); cerca, con animación.
            if (listState.firstVisibleItemIndex > 10) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .consumeWindowInsets(androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset))
        .imePadding()
    ) {
        GroupChatHeader(
            state, onBack = onBack, onOpenMeetup = onOpenMeetup,
            onDirections = { openDirections(context, state.schoolLat!!, state.schoolLon!!, state.schoolName) },
            onInvite = { id -> viewModel.shareInvite(context, id, state.name) },
            onToggleMute = { viewModel.toggleMute() }
        )
        if (state.meetupId != null) {
            var showEditGear by remember { mutableStateOf(false) }
            GroupChatGearRow(state.gearSummary) { showEditGear = true }
            if (showEditGear) {
                EditGearDialog(
                    discipline = state.discipline,
                    currentGearJson = state.myGearJson,
                    gym = state.isGym,
                    placeName = state.placeName,
                    members = state.members,
                    myUid = state.myUid,
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
            val dayStarts = chatDayStarts(state.messages)
            items(state.messages.reversed(), key = { it.id }) { msg ->
                // El separador de día va dentro del item, encima de la burbuja (como en el chat 1 a 1).
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayStarts[msg.id]?.let { ChatDayDivider(it) }
                    GroupMessageBubble(
                        msg = msg,
                        myUid = state.myUid,
                        senderName = state.memberNames[msg.fromUid] ?: "",
                        nameFor = { uid -> if (uid == state.myUid) AppText.get(R.string.school_presence_row_v3_tu) else state.memberNames[uid] ?: "" },
                        onReply = { viewModel.startReply(msg) }
                    )
                }
            }
            // reverseLayout: se pinta arriba del todo; cargar antiguos no mueve el scroll.
            if (state.canLoadMore) {
                item(key = "load_older") {
                    LoadOlderRow(onClick = { viewModel.loadOlder() })
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (!state.canWrite) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.group_chat_screen_v2_ya_no_eres_miembro_de),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            state.replyingTo?.let { reply ->
                val who = if (reply.fromUid == state.myUid) stringResource(R.string.school_presence_row_v3_tu) else (state.memberNames[reply.fromUid] ?: "")
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(3.dp).height(34.dp).background(MaterialTheme.colorScheme.primary))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(if (who.isNotBlank()) stringResource(R.string.chat_screen_v3_respondiendo_a, who) else stringResource(R.string.chat_screen_v3_respondiendo),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(reply.text, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { viewModel.cancelReply() }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.group_chat_screen_v2_cancelar_respuesta),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            ChatInputPill(text = text, onTextChange = { text = it }, onSend = { if (text.isNotBlank()) { viewModel.send(text); text = "" } })
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
internal fun GroupMessageBubble(
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
                .clip(chatBubbleShape(isMine))
                .background(bg)
                .border(1.dp, if (isMine) Color.Transparent else MaterialTheme.colorScheme.outline, chatBubbleShape(isMine))
                .padding(horizontal = 12.dp, vertical = 8.dp)
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

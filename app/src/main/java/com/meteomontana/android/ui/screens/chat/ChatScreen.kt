package com.meteomontana.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.domain.port.ChatService
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }

    // Auto-scroll al último mensaje al recibir uno nuevo.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            kotlinx.coroutines.delay(50)
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // Auto-scroll al último mensaje cuando aparece el teclado (para que los
    // últimos mensajes no queden tapados al abrir el teclado).
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && state.messages.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()
        .imePadding()
    ) {
        ChatTopBar(
            name = state.otherProfile?.username ?: state.otherProfile?.displayName ?: "Usuario",
            avatarUrl = state.otherProfile?.photoUrl,
            onBack = onBack,
            onOpenProfile = { onOpenProfile(state.otherUid) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(
                    msg = msg,
                    myUid = state.myProfile?.uid,
                    otherName = state.otherProfile?.username
                        ?: state.otherProfile?.displayName ?: "Usuario",
                    onReply = { viewModel.startReply(msg) }
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (!state.canWrite) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No puedes escribir: este perfil es privado y no te sigue",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Cita del mensaje al que respondo (estilo WhatsApp).
            state.replyingTo?.let { reply ->
                val who = if (reply.fromUid == state.myProfile?.uid) "Tú"
                          else (state.otherProfile?.username ?: state.otherProfile?.displayName ?: "")
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(3.dp).height(34.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            if (who.isNotBlank()) "Respondiendo a $who" else "Respondiendo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            reply.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
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
                androidx.compose.material3.TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje",
                        style = MaterialTheme.typography.bodyMedium) },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
                IconButton(
                    onClick = {
                        if (text.isNotBlank()) { viewModel.send(text); text = "" }
                    },
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
private fun ChatTopBar(name: String, avatarUrl: String?, onBack: () -> Unit, onOpenProfile: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onBackground)
        }
        // Avatar + nombre → abre el perfil del otro usuario.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenProfile)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (avatarUrl != null) {
                AsyncImage(model = avatarUrl, contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            } else {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        com.meteomontana.android.R.drawable.logo_cumbre),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
            Text("@$name", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun MessageBubble(
    msg: ChatService.ChatMessage,
    myUid: String?,
    otherName: String,
    onReply: (ChatService.ChatMessage) -> Unit
) {
    val isMine = msg.fromUid == myUid
    val bg = if (isMine) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surface
    val fg = if (isMine) Color.White
             else MaterialTheme.colorScheme.onSurface

    // Deslizar la burbuja a la derecha para responder (estilo WhatsApp). Al pasar
    // el umbral se dispara onReply y la burbuja vuelve a su sitio.
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
        // Icono de responder que asoma al deslizar.
        Icon(
            Icons.Outlined.Reply, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
        )
        Row(modifier = Modifier.fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) },
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
            Column(modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Cita del mensaje al que responde este mensaje. Copiamos a locals
                // porque replyText/replyToId son propiedades de otro módulo (shared)
                // y Kotlin no permite smart-cast directo tras el null-check.
                val replyText = msg.replyText
                if (msg.replyToId != null && replyText != null) {
                    val who = if (msg.replyFromUid == myUid) "Tú" else otherName
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(fg.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Box(Modifier.width(3.dp).height(28.dp).background(fg.copy(alpha = 0.5f)))
                        Column(Modifier.padding(start = 6.dp)) {
                            Text(who, style = MaterialTheme.typography.labelMedium,
                                color = fg.copy(alpha = 0.9f))
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

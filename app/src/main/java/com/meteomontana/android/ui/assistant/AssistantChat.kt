package com.meteomontana.android.ui.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.AssistantOption
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraBg
import com.meteomontana.android.ui.theme.inkButtonColor
import com.meteomontana.android.ui.theme.terraFillColor
import com.meteomontana.android.util.AppLanguage

/** Las palabras del chat (cabecera, entrada, estados) en el idioma de la app. */
private class ChatWords(lang: String) {
    private val en = lang.lowercase().startsWith("en")
    private fun t(es: String, english: String) = if (en) english else es
    val bubbleCd = t("Asistente de Cumbre", "Cumbre assistant")
    val newChat = t("NUEVA", "NEW")
    val close = t("Cerrar", "Close")
    val thinking = t("Pensando…", "Thinking…")
    val queued = t("en cola", "queued")
    val listening = t("Escuchando… se envía solo al callar. Toca el micrófono para parar y editar.",
        "Listening… it sends itself when you stop talking. Tap the mic to stop and edit.")
    val heardNothing = t("No te he oído. Toca el micrófono e inténtalo otra vez.", "I didn't hear you. Tap the mic and try again.")
    val welcomeTitle = t("¿Qué buscas hoy?", "What are you looking for today?")
    val welcomeBody = t("Pregúntame por escuelas, bloques, vías, el tiempo o la sombra de un sector. También puedes dictarlo.",
        "Ask me about crags, boulders, routes, the weather or a sector's shade. You can also dictate it.")
    val holdHint = t("Mantén pulsado el círculo para hablar directamente.", "Press and hold the circle to talk directly.")
    val placeholder = t("Pregunta sobre escuelas, tiempo o bloques…", "Ask about crags, weather or boulders…")
    val cancel = t("CANCELAR", "CANCEL")
    val confirm = t("CONFIRMAR", "CONFIRM")
    val retry = t("REINTENTAR", "RETRY")
    val stopDictation = t("Parar el dictado", "Stop dictation")
    val dictate = t("Dictar por voz", "Dictate by voice")
    val send = t("Enviar", "Send")
    val micTitle = t("Micrófono", "Microphone")
    val micMessage = t("Para dictar necesito permiso de micrófono. Puedes dárselo en Ajustes.", "To dictate I need microphone permission. You can grant it in Settings.")
    val settings = t("Ajustes", "Settings")
    val micUnavailable = t("Este móvil no tiene reconocimiento de voz.", "This phone has no speech recognition.")
}

/**
 * Burbuja flotante del asistente: un círculo con la mascota, siempre abajo a la derecha, que abre el chat. La coloca
 * MainScreen por encima de la barra. Toque = abrir el chat; mantener pulsado = abrirlo YA escuchando: hablas y miras lo
 * que te ofrece.
 *
 * [onOpenTarget] = lo que el usuario toca en el chat y hay que abrir en la app (escuela y, si se sabe, su vía): el chat
 * se cierra y se abre el destino.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssistantBubble(
    viewModel: AssistantViewModel,
    onOpenTarget: (schoolId: String, viaId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val words = ChatWords(AppLanguage.effective(LocalContext.current))

    Box(
        modifier = modifier
            .size(58.dp)
            .semantics { contentDescription = words.bubbleCd }
            .combinedClickable(
                onClick = { open = true },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setListenOnOpen(true)
                    open = true
                }
            )
    ) {
        // El círculo del dibujo ocupa ~89 % del cuadrado, pegado arriba a la izquierda: la sombra va con ESE círculo.
        Box(Modifier.size(52.dp).offset(0.5.dp, 0.5.dp).shadow(3.dp, CircleShape))
        Image(painterResource(R.drawable.assistant_bubble), contentDescription = null, modifier = Modifier.fillMaxSize())
    }

    // Al tocar un resultado el chat se cierra y se abre el destino.
    val request = state.openRequest
    LaunchedEffect(request) {
        if (request != null) {
            open = false
            viewModel.consumeOpenRequest()
            onOpenTarget(request.schoolId, request.viaId)
        }
    }

    if (open) AssistantChatSheet(viewModel, onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantChatSheet(vm: AssistantViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val words = ChatWords(AppLanguage.effective(context))
    val presenter = vm.presenter
    val assistantWords = remember { AssistantWords(AppLanguage.effective(context)) }
    val voice = remember { AssistantVoiceInput(context, scope) }
    var showMicDenied by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start() else showMicDenied = true
    }
    fun toggleVoice() {
        when {
            voice.isListening -> voice.stop()
            !voice.isAvailable() -> Unit
            voice.hasPermission() -> voice.start()
            else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        voice.onText = { vm.setInput(it) }
        // Al callar, la pregunta se envía sola.
        voice.onFinished = { spoken -> vm.send(spoken) }
        if (vm.state.value.listenOnOpen) {
            vm.setListenOnOpen(false)
            if (voice.hasPermission()) voice.start() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        onDispose { voice.stop() }
    }

    // Una hoja a casi pantalla completa, pero NO un ModalBottomSheet: su gesto de arrastrar para cerrar peleaba con el
    // scroll de la lista (un deslizamiento rápido hacia abajo cerraba el chat y el scroll iba "pesado"). Se cierra con
    // Cerrar, tocando fuera o con atrás, como pide iOS en la práctica.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
                Header(state.messages.isNotEmpty(), words, onNew = { voice.stop(); vm.reset() }, onClose = { voice.stop(); onDismiss() })
                MessagesList(vm, state, presenter, assistantWords, words, Modifier.weight(1f))
                VoiceHint(voice, words)
                InputBar(vm, state, voice, words, onToggleVoice = ::toggleVoice, onSend = { voice.stop(); vm.send() })
            }
        }
    }

    if (showMicDenied) AlertDialog(
        onDismissRequest = { showMicDenied = false },
        title = { Text(words.micTitle) },
        text = { Text(words.micMessage) },
        confirmButton = {
            TextButton(onClick = {
                showMicDenied = false
                runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null)))
                }
            }) { Text(words.settings) }
        },
        dismissButton = { TextButton(onClick = { showMicDenied = false }) { Text(words.cancel.lowercase().replaceFirstChar { it.uppercase() }) } }
    )
}

@Composable
private fun Header(hasMessages: Boolean, words: ChatWords, onNew: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (hasMessages) Text(words.newChat, color = Terra, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable(onClick = onNew).padding(horizontal = 14.dp, vertical = 10.dp))
        }
        Text("Cumbre", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(words.close, color = Terra, fontSize = 15.sp,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun MessagesList(
    vm: AssistantViewModel, state: AssistantUiState, presenter: AssistantPresenter,
    aw: AssistantWords, words: ChatWords, modifier: Modifier
) {
    val listState = rememberLazyListState()
    val itemCount = state.messages.size + (if (state.messages.isEmpty()) 1 else 0) + (if (state.isLoading) 1 else 0) + state.queued.size
    // Al abrir el chat, directamente en lo último que se habló; y al llegar un mensaje, hasta él.
    LaunchedEffect(state.messages.size, state.isLoading) {
        if (itemCount > 0) listState.animateScrollToItem(maxOf(0, itemCount - 1))
    }
    LazyColumn(modifier.fillMaxWidth(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (state.messages.isEmpty()) item(key = "welcome") { Welcome(vm, presenter, words) }
        items(state.messages.size, key = { state.messages[it].id }) { i -> MessageView(vm, state.messages[i], state.isLoading, presenter, aw, words) }
        if (state.isLoading) item(key = "loading") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = Terra, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(words.thinking, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        // Preguntas que esperan su turno (se contestan por orden).
        items(state.queued.size, key = { "queued-$it-${state.queued[it]}" }) { i ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.queued[i], color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(inkButtonColor().copy(alpha = 0.55f)).padding(horizontal = 13.dp, vertical = 10.dp))
                    Text(words.queued, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun Welcome(vm: AssistantViewModel, presenter: AssistantPresenter, words: ChatWords) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(words.welcomeTitle, color = MaterialTheme.colorScheme.onBackground, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(words.welcomeBody, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(words.holdHint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        presenter.suggestions().forEach { s ->
            Text(s, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable { vm.send(s) }.padding(horizontal = 12.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun MessageView(
    vm: AssistantViewModel, m: AssistantMessage, loading: Boolean,
    presenter: AssistantPresenter, aw: AssistantWords, words: ChatWords
) {
    if (m.isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(m.text, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.padding(start = 44.dp).clip(RoundedCornerShape(12.dp)).background(inkButtonColor()).padding(horizontal = 13.dp, vertical = 10.dp))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(m.text, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(horizontal = 13.dp, vertical = 10.dp))
        if (m.chips.isNotEmpty()) ChipsRow(m.chips)
        m.recommendation?.let { AssistantRecommendationView(it, presenter, aw) { id -> vm.requestOpen(id) } }
        m.breakdown?.let { AssistantBreakdownView(it, presenter, aw) { school, via -> vm.requestOpen(school, via) } }
        m.weather?.let { AssistantWeatherCard(it, presenter, aw) }
        if (m.journalEntries.isNotEmpty()) AssistantJournalListView(m.journalEntries, m.journalTotal, presenter) { school, via -> vm.requestOpen(school, via) }
        m.retryText?.let { RetryButton(words, enabled = !loading) { vm.retry(it) } }
        if (m.pendingActions.any { it.state == PendingAction.State.WAITING }) ConfirmButtons(vm, m, words, enabled = !loading)
        if (m.hits.isNotEmpty()) AssistantHitsView(m.hits, presenter, aw) { school, via -> vm.requestOpen(school, via) }
        if (m.restored && m.hasCards) presenter.savedNote(m.createdAt)?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (m.options.isNotEmpty()) OptionsColumn(vm, m.options)
    }
}

@Composable
private fun ChipsRow(chips: List<String>) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { chip ->
            Text(chip, color = Terra, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(TerraBg.copy(alpha = 0.85f))
                    .border(1.dp, Terra.copy(alpha = 0.35f), RoundedCornerShape(2.dp)).padding(horizontal = 9.dp, vertical = 5.dp))
        }
    }
}

/** Tras un fallo pasajero, repetir la misma pregunta con un toque (sin escribirla de nuevo). */
@Composable
private fun RetryButton(words: ChatWords, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(2.dp)).background(terraFillColor().copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Outlined.Refresh, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(words.retry, color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp)
    }
}

/** CONFIRMAR / CANCELAR para una acción propuesta: la app nunca hace nada por su cuenta. */
@Composable
private fun ConfirmButtons(vm: AssistantViewModel, m: AssistantMessage, words: ChatWords, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        m.pendingActions.filter { it.state == PendingAction.State.WAITING }.forEach { action ->
            Text(
                confirmTitle(action, m.pendingActions.size > 1, words),
                color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp,
                modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(terraFillColor().copy(alpha = if (enabled) 1f else 0.5f))
                    .clickable(enabled = enabled) { vm.confirm(m.id, action.id) }.padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
        Text(words.cancel, color = MaterialTheme.colorScheme.onBackground, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .clickable(enabled = enabled) { vm.cancel(m.id) }.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}

/** "CONFIRMAR", o con varias candidatas el nombre de cada una para poder elegir. */
private fun confirmTitle(action: PendingAction, several: Boolean, words: ChatWords): String {
    val hit = action.hit
    if (!several || hit == null) return words.confirm
    val grade = hit.grade?.let { " · $it" } ?: ""
    return "${hit.lineName ?: hit.blockName}$grade · ${hit.schoolName}"
}

@Composable
private fun OptionsColumn(vm: AssistantViewModel, options: List<AssistantOption>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            Text(option.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(2.dp))
                    .clickable { vm.send(option.name) }.padding(horizontal = 12.dp, vertical = 10.dp))
        }
    }
}

/** Estado del micrófono, justo encima de la caja: qué está pasando y cómo pararlo. */
@Composable
private fun VoiceHint(voice: AssistantVoiceInput, words: ChatWords) {
    if (voice.isListening) {
        Row(Modifier.fillMaxWidth().background(TerraBg.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(color = Terra, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Text(words.listening, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    } else if (voice.heardNothing) {
        Text(words.heardNothing, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun InputBar(
    vm: AssistantViewModel, state: AssistantUiState, voice: AssistantVoiceInput, words: ChatWords,
    onToggleVoice: () -> Unit, onSend: () -> Unit
) {
    Column {
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.input, onValueChange = vm::setInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text(words.placeholder, fontSize = 14.sp) },
                maxLines = 4,
                shape = RoundedCornerShape(2.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            val listening = voice.isListening
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(2.dp)).background(if (listening) terraFillColor() else TerraBg)
                    .border(1.dp, Terra, RoundedCornerShape(2.dp)).clickable(onClick = onToggleVoice)
                    .semantics { contentDescription = if (listening) words.stopDictation else words.dictate },
                contentAlignment = Alignment.Center
            ) { Icon(if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic, null, tint = if (listening) Color.White else Terra) }
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (state.canSend) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant)
                    .clickable(enabled = state.canSend, onClick = onSend)
                    .semantics { contentDescription = words.send },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Outlined.Send, null, tint = Color.White) }
        }
    }
}

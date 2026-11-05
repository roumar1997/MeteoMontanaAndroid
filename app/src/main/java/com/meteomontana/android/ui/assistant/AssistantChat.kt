package com.meteomontana.android.ui.assistant

import androidx.compose.foundation.gestures.detectDragGestures

import androidx.compose.ui.input.pointer.pointerInput

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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.CameraAlt
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
import com.meteomontana.android.ui.assistant.photo.AssistantPhotoMatchesView
import com.meteomontana.android.ui.assistant.photo.StonePhotoWords
import com.meteomontana.android.ui.assistant.photo.rememberStonePhotoSource
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
    val listening = t("Escuchando · se envía solo al callar",
        "Listening · it sends itself when you stop")
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
    val assistant = t("Asistente", "Assistant")
    val typing = t("Escribiendo…", "Typing…")
    val realData = t("Responde con datos reales", "Answers with real data")
    val newConversation = t("Nueva conversación", "New conversation")
    val assistantSettings = t("Ajustes del asistente", "Assistant settings")
    val spokenAnswers = t("Respuestas habladas", "Spoken answers")
    val whenSpoken = t("Si pregunto hablando", "When I ask by voice")
    val always = t("Siempre", "Always")
    val never = t("Nunca", "Never")
    val voiceOff = t("Quitar la voz", "Turn voice off")
    val voiceOn = t("Activar la voz", "Turn voice on")
    val readAloud = t("Leer en voz alta", "Read aloud")
    val stopReading = t("Parar de leer", "Stop reading")
    val stop = t("Parar", "Stop")
    val stopAnswer = t("Parar la respuesta", "Stop the answer")
    val myHome = t("MI CASA", "MY HOME")
    val saveHome = t("Guardar aquí como mi casa", "Save here as my home")
    val forgetHome = t("Olvidar mi casa", "Forget my home")
    /** La frase que se envía al tocar «Guardar aquí como mi casa» (el asistente pide confirmar). */
    val saveHomePhrase = t("guarda aquí como mi casa", "save here as my home")
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
    onOpenMeetup: (com.meteomontana.android.ui.screens.meetups.MeetupPrefill) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val words = ChatWords(AppLanguage.effective(LocalContext.current))
    // Dónde la dejaste: se puede arrastrar y se recuerda (Álvaro, 2026-10-19). Desplazamiento desde su esquina.
    val prefs = LocalContext.current.getSharedPreferences("assistant_bubble", android.content.Context.MODE_PRIVATE)
    var dx by remember { mutableStateOf(prefs.getFloat("dx", 0f)) }
    var dy by remember { mutableStateOf(prefs.getFloat("dy", 0f)) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val maxLeft = with(density) { (config.screenWidthDp.dp - 90.dp).toPx() }
    val maxUp = with(density) { (config.screenHeightDp.dp - 260.dp).toPx() }

    Box(
        modifier = modifier
            .offset { androidx.compose.ui.unit.IntOffset(dx.toInt(), dy.toInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { prefs.edit().putFloat("dx", dx).putFloat("dy", dy).apply() }
                ) { change, drag ->
                    change.consume()
                    dx = (dx + drag.x).coerceIn(-maxLeft, 0f)
                    dy = (dy + drag.y).coerceIn(-maxUp, 0f)
                }
            }
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
    // "Crea una quedada…" confirmada: el chat se cierra y se abre el formulario ya relleno.
    val meetup = state.meetupRequest
    LaunchedEffect(meetup) {
        if (meetup != null) {
            open = false
            viewModel.consumeMeetupRequest()
            onOpenMeetup(meetup)
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
        voice.onFinished = { spoken -> vm.send(spoken, spoken = true) }
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
                Header(vm, state, words, onNew = { voice.stop(); vm.reset() }, onClose = { voice.stop(); vm.speaker.stop(); onDismiss() })
                MessagesList(vm, state, presenter, assistantWords, words, Modifier.weight(1f))
                InputBar(vm, state, voice, words, onToggleVoice = ::toggleVoice, onSend = { voice.stop(); vm.send() })
                VoiceHint(voice, words)
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

/**
 * Cabecera como la de iOS: altavoz (un toque y deja de hablar) a la izquierda; Cumbri, «Asistente» y qué está haciendo
 * en el centro; y a la derecha el menú «⋯» (cuándo contesta hablando), nueva conversación y cerrar.
 */
@Composable
private fun Header(vm: AssistantViewModel, state: AssistantUiState, words: ChatWords, onNew: () -> Unit, onClose: () -> Unit) {
    val mode by vm.speaker.mode.collectAsStateWithLifecycle()
    val voiceOn = mode != AssistantSpeechMode.NEVER
    val hasHome by vm.hasHome.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            ToolbarIcon(
                if (voiceOn) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                if (voiceOn) words.voiceOff else words.voiceOn
            ) { vm.speaker.setMode(if (voiceOn) AssistantSpeechMode.NEVER else AssistantSpeechMode.WHEN_SPOKEN) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.assistant_bubble), contentDescription = null, modifier = Modifier.size(30.dp))
            Column {
                Text(words.assistant, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(if (state.isLoading) words.typing else words.realData,
                    color = if (state.isLoading) Terra else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Box {
                ToolbarIcon(Icons.Outlined.MoreHoriz, words.assistantSettings) { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    Text(words.spokenAnswers, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    listOf(
                        AssistantSpeechMode.WHEN_SPOKEN to words.whenSpoken,
                        AssistantSpeechMode.ALWAYS to words.always,
                        AssistantSpeechMode.NEVER to words.never
                    ).forEach { (option, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = if (option == mode) Terra else MaterialTheme.colorScheme.onBackground,
                                fontWeight = if (option == mode) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { vm.speaker.setMode(option); menuOpen = false }
                        )
                    }
                    // La casa para "cerca de casa": se guarda solo en este móvil.
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text(words.myHome, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    if (hasHome) {
                        DropdownMenuItem(
                            text = { Text(words.forgetHome, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Home, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { vm.forgetHome(); menuOpen = false }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(words.saveHome) },
                            leadingIcon = { Icon(Icons.Outlined.Home, null) },
                            onClick = { vm.send(words.saveHomePhrase); menuOpen = false }
                        )
                    }
                }
            }
            if (state.messages.isNotEmpty()) ToolbarIcon(Icons.Outlined.Edit, words.newConversation, onNew)
            ToolbarIcon(Icons.Outlined.Close, words.close, onClose)
        }
    }
}

/** Botón redondo de la cabecera (mismo aspecto que en iOS: círculo de papel con borde fino). */
@Composable
private fun ToolbarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(17.dp)) }
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
        items(state.messages.size, key = { state.messages[it].id }) { i -> MessageView(vm, state.messages[i], state.isLoading, presenter, aw, words,
            showFollowUps = i == state.messages.lastIndex && !state.isLoading && state.queued.isEmpty()) }
        if (state.isLoading) item(key = "loading") {
            BotRow {
                TypingDots(words.thinking)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.loadingNote?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                            .clickable { vm.stopThinking() }.padding(horizontal = 12.dp, vertical = 6.dp)
                            .semantics { contentDescription = words.stopAnswer },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Outlined.Stop, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(11.dp))
                        Text(words.stop, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
        // Preguntas que esperan su turno (se contestan por orden).
        items(state.queued.size, key = { "queued-$it-${state.queued[it]}" }) { i ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(state.queued[i], color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.padding(start = 44.dp).alpha(0.55f).clip(UserBubbleShape).background(terraFillColor())
                            .padding(horizontal = 14.dp, vertical = 10.dp))
                    Text("${words.queued} · ${i + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
        }
    }
}

/** Bocadillos con la esquina del lado de quien habla casi en punta (estilo mensajería), como en iOS. */
private val UserBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 5.dp, bottomStart = 18.dp)
private val BotBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 5.dp)

/** Fila del asistente: Cumbri pequeño a la izquierda y su contenido al lado. */
@Composable
private fun BotRow(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Image(painterResource(R.drawable.assistant_bubble), contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Tres puntos que laten mientras el asistente piensa. */
@Composable
private fun TypingDots(description: String) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    Row(
        Modifier.shadow(2.dp, BotBubbleShape).clip(BotBubbleShape).background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 13.dp).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(550), androidx.compose.animation.core.RepeatMode.Reverse,
                    androidx.compose.animation.core.StartOffset(i * 180)),
                label = "dot$i"
            )
            Box(Modifier.size(7.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

@Composable
private fun Welcome(vm: AssistantViewModel, presenter: AssistantPresenter, words: ChatWords) {
    val name = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName }
    val icons = listOf(Icons.Outlined.NearMe, Icons.Outlined.WbSunny, Icons.Outlined.Contrast)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.cumbri_welcome), contentDescription = null, modifier = Modifier.height(130.dp))
        Text(presenter.greeting(name), color = MaterialTheme.colorScheme.onBackground, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(words.welcomeBody, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presenter.suggestions().forEachIndexed { i, s ->
                val card = RoundedCornerShape(14.dp)
                Row(
                    Modifier.fillMaxWidth().shadow(2.dp, card).clip(card).background(MaterialTheme.colorScheme.surface)
                        .clickable { vm.send(s) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(TerraBg), contentAlignment = Alignment.Center) {
                        Icon(icons[i % icons.size], null, tint = Terra, modifier = Modifier.size(16.dp))
                    }
                    Text(s, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp).rotate(-45f))
                }
            }
        }
        Text(words.holdHint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun MessageView(
    vm: AssistantViewModel, m: AssistantMessage, loading: Boolean,
    presenter: AssistantPresenter, aw: AssistantWords, words: ChatWords, showFollowUps: Boolean
) {
    if (m.isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(m.text, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.padding(start = 44.dp).clip(UserBubbleShape).background(terraFillColor()).padding(horizontal = 14.dp, vertical = 10.dp))
        }
        return
    }
    val lang = AppLanguage.effective(LocalContext.current)
    BotRow {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.text, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
                modifier = Modifier.weight(1f, fill = false).shadow(2.dp, BotBubbleShape).clip(BotBubbleShape)
                    .background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 10.dp))
            SpeakerButton(vm, m, lang, words)
        }
        if (m.photoMatches.isNotEmpty()) AssistantPhotoMatchesView(m.photoMatches, StonePhotoWords(lang)) { c -> vm.requestOpen(c.schoolId, c.lineId) }
        if (m.chips.isNotEmpty()) ChipsRow(m.chips, presenter)
        m.restrictions?.let { AssistantRestrictionsView(it, presenter) { id -> vm.requestOpen(id) } }
        if (m.trip.isNotEmpty()) AssistantTripView(m.trip, presenter) { id -> vm.requestOpen(id) }
        m.recommendation?.let { AssistantRecommendationView(it, presenter, aw) { id -> vm.requestOpen(id) } }
        m.breakdown?.let { AssistantBreakdownView(it, presenter, aw) { school, via -> vm.requestOpen(school, via) } }
        m.weather?.let { AssistantWeatherCard(it, presenter, aw) }
        if (m.journalEntries.isNotEmpty()) AssistantJournalListView(m.journalEntries, m.journalTotal, presenter) { school, via -> vm.requestOpen(school, via) }
        m.retryText?.let { RetryButton(words, enabled = !loading) { vm.retry(it) } }
        if (m.pendingActions.any { it.state == PendingAction.State.WAITING }) ConfirmButtons(vm, m, presenter, enabled = !loading)
        if (m.hits.isNotEmpty()) AssistantHitsView(m.hits, presenter, aw) { school, via -> vm.requestOpen(school, via) }
        if (m.restored && m.hasCards) presenter.savedNote(m.createdAt)?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (m.options.isNotEmpty()) OptionsColumn(vm, m.options)
        if (showFollowUps) FollowUpsRow(vm, presenter.followUps(m))
    }
}

/** Ideas para seguir preguntando debajo de la última respuesta: se envían tal cual o abren la escuela. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FollowUpsRow(vm: AssistantViewModel, items: List<AssistantPresenter.FollowUp>) {
    if (items.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            Text(item.title, color = Terra, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.5.dp, Terra, RoundedCornerShape(50))
                    .clickable { item.openSchoolId?.let { vm.requestOpen(it) } ?: vm.send(item.title) }
                    .padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

/** Lo que ha entendido, en pastillas con un icono pequeño (sitio, sol/sombra, cuándo…). */
@Composable
private fun ChipsRow(chips: List<String>, presenter: AssistantPresenter) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { chip ->
            Row(
                Modifier.clip(CircleShape).background(TerraBg).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presenter.chipIcon(chip)?.let { Icon(chipVector(it), null, tint = Terra, modifier = Modifier.size(11.dp)) }
                Text(chip, color = Terra, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

private fun chipVector(icon: AssistantPresenter.ChipIcon) = when (icon) {
    AssistantPresenter.ChipIcon.SHADE -> Icons.Outlined.Contrast
    AssistantPresenter.ChipIcon.SUN -> Icons.Outlined.WbSunny
    AssistantPresenter.ChipIcon.MORNING -> Icons.Outlined.WbTwilight
    AssistantPresenter.ChipIcon.AFTERNOON -> Icons.Outlined.WbTwilight
    AssistantPresenter.ChipIcon.BOULDER -> Icons.Outlined.ViewInAr
    AssistantPresenter.ChipIcon.ROUTE -> Icons.Outlined.VerticalAlignTop
    AssistantPresenter.ChipIcon.LOCATION -> Icons.Outlined.NearMe
    AssistantPresenter.ChipIcon.STAR -> Icons.Outlined.StarOutline
    AssistantPresenter.ChipIcon.NO_RAIN -> Icons.Outlined.WbCloudy
    AssistantPresenter.ChipIcon.TOPO -> Icons.Outlined.Image
    AssistantPresenter.ChipIcon.NOT_DONE -> Icons.Outlined.RadioButtonUnchecked
    AssistantPresenter.ChipIcon.MOST_LINES -> Icons.Outlined.FormatListNumbered
    AssistantPresenter.ChipIcon.BETA -> Icons.Outlined.Accessibility
    AssistantPresenter.ChipIcon.FACE -> Icons.Outlined.Explore
    AssistantPresenter.ChipIcon.CALENDAR -> Icons.Outlined.CalendarMonth
    AssistantPresenter.ChipIcon.PIN -> Icons.Outlined.Place
}

/** Botón de píldora terracota con icono (Reintentar, Confirmar, Apuntar las N), como en iOS. */
@Composable
private fun PrimaryPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(terraFillColor().copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Tras un fallo pasajero, repetir la misma pregunta con un toque (sin escribirla de nuevo). */
@Composable
private fun RetryButton(words: ChatWords, enabled: Boolean, onClick: () -> Unit) =
    PrimaryPill(words.retry.lowercase().replaceFirstChar { it.uppercase() }, Icons.Outlined.Refresh, enabled, onClick)

@Composable
private fun CancelPill(presenter: AssistantPresenter, enabled: Boolean, onClick: () -> Unit) {
    Text(presenter.cancelWord(), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = Modifier.clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp))
}

/** Confirmar / Cancelar para una acción propuesta: la app nunca hace nada por su cuenta. */
@Composable
private fun ConfirmButtons(vm: AssistantViewModel, m: AssistantMessage, presenter: AssistantPresenter, enabled: Boolean) {
    val waiting = m.pendingActions.filter { it.state == PendingAction.State.WAITING }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            m.confirmAll -> {
                // Varias vías del día: se enseñan todas y se apuntan con un solo toque.
                waiting.forEach { action ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(if (action.kind == "LOG_PROJECT") Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle,
                            null, tint = Terra, modifier = Modifier.size(18.dp))
                        Text(logLine(action, presenter), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryPill(presenter.logAll(waiting.size), Icons.Outlined.Check, enabled) { vm.confirmAll(m.id) }
                    CancelPill(presenter, enabled) { vm.cancel(m.id) }
                }
            }
            waiting.size > 1 -> {
                // Varias candidatas: una tarjeta por cada una para elegir, y Cancelar debajo.
                waiting.forEach { action -> OptionCard(confirmTitle(action, presenter), enabled) { vm.confirm(m.id, action.id) } }
                CancelPill(presenter, enabled) { vm.cancel(m.id) }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                waiting.forEach { action -> PrimaryPill(presenter.confirmWord(), Icons.Outlined.Check, enabled) { vm.confirm(m.id, action.id) } }
                CancelPill(presenter, enabled) { vm.cancel(m.id) }
            }
        }
    }
}

/** "Zarzamora · 7a · Albarracín — hecha" / "— proyecto". */
private fun logLine(action: PendingAction, p: AssistantPresenter): String {
    val hit = action.hit ?: return action.schoolName
    val grade = hit.grade?.let { " · $it" } ?: ""
    val what = if (action.kind == "LOG_PROJECT") p.projectWord() else p.doneWord()
    return "${hit.lineName ?: hit.blockName}$grade · ${hit.schoolName} — $what"
}

/** El nombre de cada candidata para poder elegir. */
private fun confirmTitle(action: PendingAction, p: AssistantPresenter): String {
    val hit = action.hit ?: return p.confirmWord()
    val grade = hit.grade?.let { " · $it" } ?: ""
    return "${hit.lineName ?: hit.blockName}$grade · ${hit.schoolName}"
}

/** Tarjeta redondeada tocable con flecha: opciones de "¿a cuál te refieres?" y candidatas a confirmar. */
@Composable
private fun OptionCard(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().shadow(2.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun OptionsColumn(vm: AssistantViewModel, options: List<AssistantOption>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option -> OptionCard(option.name) { vm.send(option.name) } }
    }
}

/** Estado del micrófono, debajo de la caja: corto, para no tapar la conversación. */
@Composable
private fun VoiceHint(voice: AssistantVoiceInput, words: ChatWords) {
    val center = androidx.compose.ui.text.style.TextAlign.Center
    if (voice.isListening) {
        Text(words.listening, color = Terra, fontWeight = FontWeight.Medium, fontSize = 11.sp, textAlign = center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
    } else if (voice.heardNothing) {
        Text(words.heardNothing, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, textAlign = center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
    }
}

@Composable
private fun InputBar(
    vm: AssistantViewModel, state: AssistantUiState, voice: AssistantVoiceInput, words: ChatWords,
    onToggleVoice: () -> Unit, onSend: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StoneCameraButton(vm, enabled = !state.isLoading, words = words, lang = AppLanguage.effective(LocalContext.current))
        // Caja de texto de píldora, como la de iOS: fondo de papel con sombra suave, sin borde.
        val field = RoundedCornerShape(21.dp)
        Box(
            Modifier.weight(1f).heightIn(min = 42.dp).shadow(2.dp, field).clip(field).background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (state.input.isEmpty()) {
                Text(words.placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = state.input, onValueChange = vm::setInput,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Terra),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
        }
        val listening = voice.isListening
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            if (listening) PulseRing()
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(if (listening) terraFillColor() else TerraBg)
                    .clickable(onClick = onToggleVoice)
                    .semantics { contentDescription = if (listening) words.stopDictation else words.dictate },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic, null,
                    tint = if (listening) Color.White else Terra, modifier = Modifier.size(20.dp))
            }
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .background(if (state.canSend) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant)
                .clickable(enabled = state.canSend, onClick = onSend)
                .semantics { contentDescription = words.send },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.ArrowUpward, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

/** Anillo que se expande alrededor del micrófono mientras escucha. */
@Composable
private fun PulseRing() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100)), label = "p")
    Box(Modifier.size(42.dp).scale(1f + 0.45f * p).alpha(0.7f * (1f - p)).border(2.dp, Terra, CircleShape))
}

/** Leer (o parar) esta respuesta en voz alta. */
@Composable
private fun SpeakerButton(vm: AssistantViewModel, m: AssistantMessage, lang: String, words: ChatWords) {
    val speakingId by vm.speaker.speakingId.collectAsStateWithLifecycle()
    val active = speakingId == m.id
    Box(
        Modifier.size(26.dp).clip(CircleShape)
            .background(if (active) Terra else MaterialTheme.colorScheme.surface)
            .then(if (active) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            .clickable { vm.speaker.toggle(spokenText(m, lang), m.id) }
            .semantics { contentDescription = if (active) words.stopReading else words.readAloud },
        contentAlignment = Alignment.Center
    ) {
        Icon(if (active) Icons.Outlined.Stop else Icons.Outlined.VolumeUp, null,
            tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
    }
}

/** Cámara junto a la caja de texto: «¿Qué piedra es esta?» con una foto hecha ahora o elegida de la galería. */
@Composable
private fun StoneCameraButton(vm: AssistantViewModel, enabled: Boolean, words: ChatWords, lang: String) {
    val stoneWords = remember(lang) { StonePhotoWords(lang) }
    val source = rememberStonePhotoSource(onPhoto = vm::identifyPhoto)
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(enabled = enabled) { open = true }
                .semantics { contentDescription = stoneWords.menuCd },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.PhotoCamera, null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stoneWords.takePhoto) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                onClick = { open = false; source.takePhoto() }
            )
            DropdownMenuItem(
                text = { Text(stoneWords.pickPhoto) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                onClick = { open = false; source.pickPhoto() }
            )
        }
    }
}

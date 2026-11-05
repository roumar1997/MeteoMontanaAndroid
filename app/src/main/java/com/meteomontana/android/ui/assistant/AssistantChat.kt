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
internal class ChatWords(lang: String) {
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
internal fun AssistantChatSheet(vm: AssistantViewModel, onDismiss: () -> Unit) {
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
internal fun Header(vm: AssistantViewModel, state: AssistantUiState, words: ChatWords, onNew: () -> Unit, onClose: () -> Unit) {
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
internal fun ToolbarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(17.dp)) }
}

@Composable
internal fun MessagesList(
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
internal val UserBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 5.dp, bottomStart = 18.dp)
internal val BotBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 5.dp)

/** Fila del asistente: Cumbri pequeño a la izquierda y su contenido al lado. */
@Composable
internal fun BotRow(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Image(painterResource(R.drawable.assistant_bubble), contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Tres puntos que laten mientras el asistente piensa. */
@Composable
internal fun TypingDots(description: String) {
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
internal fun Welcome(vm: AssistantViewModel, presenter: AssistantPresenter, words: ChatWords) {
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


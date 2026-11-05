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

// Barra de escribir del asistente: dictado, aviso de voz, altavoz y cámara de «¿qué piedra es esta?».

/** Estado del micrófono, debajo de la caja: corto, para no tapar la conversación. */
@Composable
internal fun VoiceHint(voice: AssistantVoiceInput, words: ChatWords) {
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
internal fun InputBar(
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
internal fun PulseRing() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100)), label = "p")
    Box(Modifier.size(42.dp).scale(1f + 0.45f * p).alpha(0.7f * (1f - p)).border(2.dp, Terra, CircleShape))
}

/** Leer (o parar) esta respuesta en voz alta. */
@Composable
internal fun SpeakerButton(vm: AssistantViewModel, m: AssistantMessage, lang: String, words: ChatWords) {
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
internal fun StoneCameraButton(vm: AssistantViewModel, enabled: Boolean, words: ChatWords, lang: String) {
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

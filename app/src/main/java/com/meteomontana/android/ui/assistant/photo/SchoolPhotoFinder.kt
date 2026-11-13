package com.meteomontana.android.ui.assistant.photo

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.util.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * «¿Qué piedra es esta?» desde la pantalla de una escuela: una foto (hecha ahora o de la galería) y la app dice cuál de
 * las piedras de ESA escuela es. Todo en el móvil: la foto y la ubicación no se envían a ningún sitio y no hay IA de por
 * medio, así que no gasta cupo ni cuesta dinero. Espejo de `SchoolPhotoFinder.swift`.
 */
sealed interface SchoolPhotoState {
    data object Idle : SchoolPhotoState
    data class Working(val note: String) : SchoolPhotoState
    /** [weak] = ni la más parecida se parece de verdad: puede que esa piedra aún no esté en Cumbre. */
    data class Found(val top: List<StoneCandidate>, val sure: Boolean, val approximate: Boolean, val weak: Boolean) : SchoolPhotoState
    data class Failed(val text: String) : SchoolPhotoState
}

@HiltViewModel
class SchoolPhotoFinderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val matcher: StonePhotoMatcher,
    private val locator: StonePhotoLocator
) : ViewModel() {
    private val _state = MutableStateFlow<SchoolPhotoState>(SchoolPhotoState.Idle)
    val state: StateFlow<SchoolPhotoState> = _state.asStateFlow()
    private val _showSheet = MutableStateFlow(false)
    val showSheet: StateFlow<Boolean> = _showSheet.asStateFlow()
    private var job: Job? = null

    private val words get() = StonePhotoWords(AppLanguage.effective(context))

    /** Cierra la hoja y descarta lo que estuviera calculándose. */
    fun cancel() {
        job?.cancel()
        _state.value = SchoolPhotoState.Idle
        _showSheet.value = false
    }

    fun fail(text: String) {
        job?.cancel()
        _state.value = SchoolPhotoState.Failed(text)
        _showSheet.value = true
    }

    fun identify(photo: StonePhotoInput, school: School) {
        job?.cancel()
        val w = words
        _state.value = SchoolPhotoState.Working(if (photo.fromCamera) w.checkingWhere else w.readingWhere)
        _showSheet.value = true
        job = viewModelScope.launch {
            try {
                val place = locator.locate(photo)
                var lastStage: String? = null
                val outcome = withTimeoutOrNull(StonePhotoMatcher.TOTAL_TIMEOUT_MS) {
                    matcher.matchInSchool(photo.uri, place.lat, place.lon, place.approximate, school) { stage ->
                        lastStage = w.stage(stage)
                        _state.update { SchoolPhotoState.Working(lastStage!!) }
                    }
                }
                _state.value = when (outcome) {
                    null -> SchoolPhotoState.Failed(w.tookTooLong(null))
                    is StoneOutcome.Found -> {
                        val weak = StonePhotoMatcher.isWeak(outcome.top)
                        SchoolPhotoState.Found(outcome.top, outcome.sure && !weak, outcome.approximate, weak)
                    }
                    is StoneOutcome.NoStones -> SchoolPhotoState.Failed(w.noStonesWithPhoto(outcome.schoolName))
                    StoneOutcome.NoData -> SchoolPhotoState.Failed(w.noDataInSchool)
                    // No se dan dentro de una escuela (siempre se compara con las suyas).
                    StoneOutcome.NoLocation, is StoneOutcome.NoSchoolNear -> SchoolPhotoState.Failed(w.cannotOpen)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }
}

/** Círculo de cámara junto al botón de ayuda del título de la escuela: menú con «Hacer una foto» y «Elegir de la galería». */
@Composable
fun SchoolPhotoButton(school: School, viewModel: SchoolPhotoFinderViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val words = remember(AppLanguage.effective(context)) { StonePhotoWords(AppLanguage.effective(context)) }
    val source = rememberStonePhotoSource(onError = { viewModel.fail(words.cannotOpen) }) { photo -> viewModel.identify(photo, school) }
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        // Botón redondo terracota con la cámara (sin nombre), junto al «? AYUDA» del título.
        Box(
            Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(com.meteomontana.android.ui.theme.terraFillColor())
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { open = true }
                .semantics { contentDescription = words.title },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(words.takePhoto) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                onClick = { open = false; source.takePhoto() }
            )
            DropdownMenuItem(
                text = { Text(words.pickPhoto) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                onClick = { open = false; source.pickPhoto() }
            )
        }
    }
}

/** Hoja con el resultado: las 3 piedras más probables con su foto. Tocar una abre esa piedra de la escuela. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolPhotoSheet(viewModel: SchoolPhotoFinderViewModel, schoolName: String, onOpenBlock: (blockId: String) -> Unit) {
    val show by viewModel.showSheet.collectAsStateWithLifecycle()
    if (!show) return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val words = remember(AppLanguage.effective(context)) { StonePhotoWords(AppLanguage.effective(context)) }
    ModalBottomSheet(
        onDismissRequest = viewModel::cancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(words.title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Icon(Icons.Outlined.Close, words.close, tint = Terra,
                    modifier = Modifier.size(24.dp).clickable { viewModel.cancel() })
            }
            when (val s = state) {
                SchoolPhotoState.Idle -> Unit
                is SchoolPhotoState.Working -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Terra, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text(s.note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    }
                    Text(words.stop, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                            .clickable { viewModel.cancel() }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                is SchoolPhotoState.Failed -> Text(s.text, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
                is SchoolPhotoState.Found -> {
                    Text(words.intro(s.sure, s.approximate, schoolName, s.weak), color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
                    if ((s.top.firstOrNull()?.meters ?: 0.0) < 0) {
                        Text(words.byLooksOnly, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                    }
                    AssistantPhotoMatchesView(s.top, words) { c -> onOpenBlock(c.blockId) }
                }
            }
        }
    }
}

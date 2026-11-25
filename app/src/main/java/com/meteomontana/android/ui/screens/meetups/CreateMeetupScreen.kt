package com.meteomontana.android.ui.screens.meetups

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.FeatureKey
import com.meteomontana.android.domain.model.MeetupDraft
import com.meteomontana.android.ui.components.FeatureOffNotice
import com.meteomontana.android.ui.components.LocalFeatureFlags
import com.meteomontana.android.ui.screens.gyms.GymActionPill
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.util.AppText
import kotlinx.serialization.json.Json

/**
 * Datos que trae el asistente («crea una quedada en Albarracín el sábado a las 9») o la ficha de un rocódromo: el
 * asistente se abre ya con el lugar (y el día) puestos y el usuario lo revisa y la crea con su botón. Espejo de
 * `AssistantViewModel.MeetupRequest` (iOS).
 */
data class MeetupPrefill(val schoolId: String, val schoolName: String, val day: String?, val name: String, val description: String?,
    /** Rocódromo de la quedada (entonces [schoolId] va en blanco). */ val gymId: Long? = null)

private val DraftSaver = Saver<androidx.compose.runtime.MutableState<MeetupDraft>, String>(
    save = { Json.encodeToString(MeetupDraft.serializer(), it.value) },
    restore = { raw -> mutableStateOf(runCatching { Json.decodeFromString(MeetupDraft.serializer(), raw) }.getOrDefault(MeetupDraft())) }
)

private const val LAST_STEP = 3

/**
 * Nueva quedada en 3 pasos (maqueta A): 1 dónde y cuándo, 2 qué y con quién (bloque o vía, cuántos buscáis, grado,
 * material mínimo), 3 privacidad, límite, nombre y foto. Atrás conserva lo elegido: el borrador entero sobrevive
 * incluso a que el sistema mate el proceso. La lógica (qué falta, la petición) vive en `MeetupDraft` de shared.
 */
@Composable
fun CreateMeetupScreen(
    onBack: () -> Unit = {},
    onCreated: (meetupId: String) -> Unit = {},
    onEditProfile: () -> Unit = {},
    prefill: MeetupPrefill? = null,
    viewModel: MeetupsViewModel = hiltViewModel()
) {
    val createError by viewModel.createError.collectAsStateWithLifecycle()
    val genderGate by viewModel.genderGate.collectAsStateWithLifecycle()
    if (genderGate) GenderGateDialog(onEditProfile = onEditProfile, onDismiss = { viewModel.clearGenderGate() })
    val uploadingPhoto by viewModel.uploadingPhoto.collectAsStateWithLifecycle()
    // Crear quedada en pausa (interruptor del servidor, o un 503 acabado de recibir): aviso en vez del asistente.
    val flags = LocalFeatureFlags.current
    val createPaused by viewModel.createPaused.collectAsStateWithLifecycle()
    val paused = if (!flags.isEnabled(FeatureKey.MEETUP_CREATE)) FeatureNotice(flags.messageFor(FeatureKey.MEETUP_CREATE)) else createPaused
    val dayScores by viewModel.dayScores.collectAsStateWithLifecycle()
    val schoolResults by viewModel.schoolResults.collectAsStateWithLifecycle()

    var draftState = rememberSaveable(saver = DraftSaver) { mutableStateOf(MeetupDraft()) }
    var draft by draftState
    var step by rememberSaveable { mutableIntStateOf(1) }
    var submitting by remember { mutableStateOf(false) }

    // Lo que trae el asistente o la ficha se aplica UNA vez: si el usuario lo cambia, no se le vuelve a poner encima.
    var prefillApplied by rememberSaveable { mutableStateOf(false) }
    // Si se llega desde una ficha (rocódromo o escuela) el lugar ya viene fijado y no se pregunta.
    var placeLocked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(prefill) {
        val p = prefill ?: return@LaunchedEffect
        if (prefillApplied) return@LaunchedEffect
        prefillApplied = true
        placeLocked = p.gymId != null || p.schoolId.isNotBlank()
        var d = draft
        d = if (p.gymId != null) d.withGym(p.gymId, p.schoolName) else if (p.schoolId.isNotBlank()) d.withSchool(p.schoolId, p.schoolName) else d
        p.day?.let { d = d.toggleDay(it) }
        draft = d.copy(name = p.name, description = p.description)
    }

    // Notas del día con escuela elegida.
    LaunchedEffect(draft.schoolId, draft.days) {
        if (!draft.placeIsGym && draft.schoolId.isNotBlank() && draft.days.isNotEmpty()) viewModel.loadMeetupDayScores(draft.schoolId, draft.days)
        else viewModel.clearDayScores()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        // Pasamos el URI: el VM lee con readImageCompressed (hornea la rotación EXIF).
        viewModel.uploadMeetupPhoto(uri.toString()) { url -> draft = draft.copy(photoUrl = url) }
    }
    val defaultName = AppText.get(R.string.gym_meetup_default_name, draft.placeName.ifBlank { AppText.get(R.string.create_meetup_title) })
    val goBack = { if (step > 1) step -= 1 else onBack() }
    BackHandler(enabled = step > 1) { step -= 1 }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meteomontana.android.feature.chat.R.string.common_back)) }
            Text(
                stringResource(R.string.create_meetup_title), modifier = Modifier.weight(1f),
                fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        if (paused != null) {
            FeatureOffNotice(paused.message, Modifier.padding(Spacing.md))
            return@Column
        }
        WizardProgress(step)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            when (step) {
                1 -> MeetupPlaceStep(draft, { draft = it }, schoolResults, { viewModel.searchSchools(it) }, dayScores, placeLocked)
                2 -> MeetupDetailsStep(draft) { draft = it }
                else -> MeetupFinalStep(draft, { draft = it }, uploadingPhoto, { photoPicker.launch("image/*") }, defaultName)
            }
            createError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        // SIN navigationBarsPadding: esta pantalla vive dentro del overlay, y el overlay YA reserva abajo la cápsula
        // de pestañas más la barra del sistema (reservarlo otra vez dejaba un hueco muerto, Redmi Note 12).
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 1) GymActionPill(stringResource(R.string.mw_back), { step -= 1 }, Modifier.weight(0.45f))
            if (step < LAST_STEP) {
                GymActionPill(
                    stringResource(R.string.mw_next), { step += 1 }, Modifier.weight(1f), primary = true,
                    enabled = draft.canAdvance(step)
                )
            } else {
                GymActionPill(
                    if (submitting) "…" else stringResource(R.string.create_meetup_create), enabled = !submitting && draft.canAdvance(1),
                    onClick = {
                        submitting = true
                        viewModel.create(
                            draft.toRequest(defaultName),
                            onSuccess = { meetup -> submitting = false; onCreated(meetup.id) },
                            onError = { submitting = false }
                        )
                    },
                    modifier = Modifier.weight(1f), primary = true
                )
            }
        }
    }
}

package com.meteomontana.android.ui.screens.gyms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.gym.BoardAngleMemory
import com.meteomontana.android.domain.model.gym.BoardKind
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.BoardLog
import com.meteomontana.android.domain.model.gym.BoardLogging
import com.meteomontana.android.domain.model.gym.BoardProblem
import com.meteomontana.android.domain.model.gym.BoardProblemRepeats
import com.meteomontana.android.domain.model.gym.BoardProblemSuggestion
import com.meteomontana.android.domain.model.gym.BoardProblems
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentKeys
import com.meteomontana.android.domain.model.gym.GymSessionSource
import com.meteomontana.android.domain.model.gym.GymTraining
import com.meteomontana.android.domain.usecase.gym.GymTrainingUseCase
import com.meteomontana.android.domain.usecase.gym.RecordGymSessionUseCase
import com.meteomontana.android.domain.usecase.gym.SearchBoardProblemsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GymTrainSheetState(val ascents: List<GymAscent> = emptyList(), val boardProblems: List<BoardProblem> = emptyList(), val saving: Boolean = false, val saved: Boolean = false,
    /** Ya había algo apuntado al abrir la hoja: dejarla a cero BORRA el entreno. */
    val hadAscents: Boolean = false,
    /** Sugerencias del nombre del bloque mientras se escribe (lo propio y el catalogo compartido). */
    val suggestions: List<BoardProblemSuggestion> = emptyList()
)

/** Hoja «He entrenado aquí hoy»: apunta lo hecho (bloques, vías, boards) y lo guarda junto con la sesión de hoy. */
@HiltViewModel
class GymTrainSheetViewModel @Inject constructor(
    private val training: GymTrainingUseCase,
    private val record: RecordGymSessionUseCase,
    private val searchProblems: SearchBoardProblemsUseCase,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
) : ViewModel() {
    /** Ultima inclinacion elegida en cada board (en el movil), para preseleccionarla. */
    val angles = BoardAngleMemory(com.meteomontana.android.data.local.SharedPrefsKeyValueStore(context, "cumbre_board_prefs"))

    private val _state = MutableStateFlow(GymTrainSheetState())
    val state: StateFlow<GymTrainSheetState> = _state

    fun load(gymId: Long, date: kotlinx.datetime.LocalDate? = null) {
        val today = training.today()
        val day = date ?: today
        val existing = training.session(gymId, day)
        suggestJob?.cancel()
        _state.update { GymTrainSheetState(ascents = existing?.ascents ?: emptyList(), boardProblems = existing?.boardProblems ?: emptyList(), hadAscents = existing?.ascents?.isNotEmpty() == true) }
    }

    fun adjust(key: String, delta: Int) = _state.update {
        val next = GymTraining.adjust(it.ascents, key, delta)
        it.copy(ascents = next, boardProblems = BoardProblems.prune(it.boardProblems, next))
    }

    /** Apunta un board y, si lleva nombre (opcional, se recorta), tambien el nombre del bloque. */
    fun addBoard(board: BoardKind, grade: String, angle: Int?, name: String, reps: Int, attempts: Int? = null) = _state.update {
        val log = BoardLogging.add(BoardLog(it.ascents, it.boardProblems), board, grade, angle, name, reps, attempts)
        it.copy(ascents = log.ascents, boardProblems = log.problems, suggestions = emptyList())
    }

    private var suggestJob: Job? = null

    /** Al escribir el nombre: al instante lo que ya usaste y, tras una pausa corta, tambien el catalogo de todas las personas (desde 2 letras). */
    fun onNameChanged(board: BoardKind, text: String) {
        suggestJob?.cancel()
        if (BoardProblemRepeats.nameKey(text).length < BoardProblemRepeats.SUGGEST_MIN_CHARS) { clearSuggestions(); return }
        _state.update { it.copy(suggestions = searchProblems.local(board, text)) }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            runCatching { searchProblems.search(board, text) }.getOrNull()?.let { found -> _state.update { it.copy(suggestions = found) } }
        }
    }

    fun clearSuggestions() {
        suggestJob?.cancel()
        _state.update { it.copy(suggestions = emptyList()) }
    }

    /** Apunta la sesión de hoy (si no estaba) y guarda lo hecho. Funciona sin red: primero en el móvil, luego se sube. */
    fun save(gymId: Long, onDone: () -> Unit, date: kotlinx.datetime.LocalDate? = null) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val day = date ?: training.today()
            // Hoy se apunta también en el servidor; otro día solo existe la ruta de «hoy», así que queda en el móvil.
            val list = _state.value.ascents
            // Con algo apuntado se da de alta el día (hoy también en el servidor); a cero solo se borra si ya había algo.
            if (gymId != GymSession.NO_GYM && list.isNotEmpty() && day == training.today()) runCatching { record.execute(gymId, GymSessionSource.MANUAL) }
            if (list.isNotEmpty() || _state.value.hadAscents) runCatching { training.saveAscents(gymId, day, list, _state.value.boardProblems) }
            _state.update { it.copy(saving = false, saved = true) }
            onDone()
        }
    }
}

private const val SUGGEST_DEBOUNCE_MS = 250L

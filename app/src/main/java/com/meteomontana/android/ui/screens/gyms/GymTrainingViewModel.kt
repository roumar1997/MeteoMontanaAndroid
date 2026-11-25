package com.meteomontana.android.ui.screens.gyms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymAscentStats
import com.meteomontana.android.domain.model.gym.GymSession
import com.meteomontana.android.domain.model.gym.GymTraining
import com.meteomontana.android.domain.model.gym.GymTrainingPeriod
import com.meteomontana.android.domain.model.gym.GymTrainingSummary
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GymTrainingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import javax.inject.Inject

data class GymTrainingState(
    val period: GymTrainingPeriod = GymTrainingPeriod.WEEK,
    val summary: GymTrainingSummary = GymTrainingSummary(GymTrainingPeriod.WEEK, emptyList(), 0, 0),
    val gyms: Map<Long, Gym> = emptyMap(),
    /** Sesión abierta para apuntar lo hecho (gymId + fecha). */
    val openKey: Pair<Long, LocalDate>? = null,
    /** Por rocódromo (el más visitado primero), solo en «Año»: ascensiones por color o grado. */
    val perGym: List<Pair<Long, GymAscentStats>> = emptyList(),
    val today: LocalDate = LocalDate(1970, 1, 1)
)

/**
 * Pantalla «Entrenos»: sesiones de la semana, el mes o el año (desde el móvil, sin red), lo hecho por color o grado
 * por sesión (primero en el móvil, luego se sube) y, en «Año», las barras por rocódromo con `gym-ascents`.
 */
@HiltViewModel
class GymTrainingViewModel @Inject constructor(
    private val training: GymTrainingUseCase,
    getCachedGyms: GetCachedGymsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(
        GymTrainingState(today = training.today(), gyms = getCachedGyms().associateBy { it.id }).let { it.copy(summary = training.summary(it.period, it.today)) }
    )
    val state: StateFlow<GymTrainingState> = _state

    private val saves = mutableMapOf<Pair<Long, LocalDate>, Job>()

    init {
        viewModelScope.launch { runCatching { training.refresh() }; reload() }
        reload()
    }

    fun setPeriod(p: GymTrainingPeriod) { _state.update { it.copy(period = p) }; reload() }

    fun toggleOpen(s: GymSession) {
        val key = s.gymId to s.date
        _state.update { it.copy(openKey = if (it.openKey == key) null else key) }
    }

    /** Relee las sesiones (tras cerrar la hoja de entreno). */
    fun refresh() { viewModelScope.launch { runCatching { training.refresh() }; reload() }; reload() }

    private fun reload() {
        val st = _state.value
        _state.update { it.copy(summary = training.summary(st.period, st.today)) }
        if (st.period == GymTrainingPeriod.YEAR) loadPerGym()
    }

    private fun loadPerGym() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val period = _state.value.period
        val today = _state.value.today
        val topGyms = _state.value.summary.sessions.groupingBy { it.gymId }.eachCount().entries
            .sortedByDescending { it.value }.take(3).map { it.key }
        viewModelScope.launch {
            val result = topGyms.mapNotNull { id ->
                runCatching { training.ascentStats(uid, period, today, id) }.getOrNull()?.takeIf { it.items.isNotEmpty() }?.let { id to it }
            }
            _state.update { if (it.period == period) it.copy(perGym = result) else it }
        }
    }

    /** + o − en un color o grado de una sesión: se ve al instante y se guarda (el último toque manda). */
    fun adjust(s: GymSession, key: String, delta: Int) {
        val k = s.gymId to s.date
        val next = GymTraining.adjust(s.ascents, key, delta)
        if (next == s.ascents) return
        // Se pinta ya con lo nuevo; saveAscents lo guarda primero en el móvil y luego lo sube.
        _state.update { st ->
            st.copy(summary = st.summary.let { sm ->
                val sessions = sm.sessions.map { if (it.gymId == s.gymId && it.date == s.date) it.copy(ascents = next, boardProblems = com.meteomontana.android.domain.model.gym.BoardProblems.prune(it.boardProblems, next)) else it }
                sm.copy(sessions = sessions, ascents = sessions.sumOf { it.totalAscents })
            })
        }
        saves[k]?.cancel()
        saves[k] = viewModelScope.launch {
            runCatching { training.saveAscents(s.gymId, s.date, next) }
            if (_state.value.period == GymTrainingPeriod.YEAR) loadPerGym()
        }
    }

    fun gymName(id: Long): String = _state.value.gyms[id]?.name ?: com.meteomontana.android.util.AppText.get(com.meteomontana.android.R.string.gym_train_title)
}

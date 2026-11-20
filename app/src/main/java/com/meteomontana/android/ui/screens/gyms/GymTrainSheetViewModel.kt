package com.meteomontana.android.ui.screens.gyms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.gym.GymAscent
import com.meteomontana.android.domain.model.gym.GymSessionSource
import com.meteomontana.android.domain.model.gym.GymTraining
import com.meteomontana.android.domain.usecase.gym.GymTrainingUseCase
import com.meteomontana.android.domain.usecase.gym.RecordGymSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GymTrainSheetState(val ascents: List<GymAscent> = emptyList(), val saving: Boolean = false, val saved: Boolean = false)

/** Hoja «He entrenado aquí hoy»: apunta lo hecho (bloques, vías, boards) y lo guarda junto con la sesión de hoy. */
@HiltViewModel
class GymTrainSheetViewModel @Inject constructor(
    private val training: GymTrainingUseCase,
    private val record: RecordGymSessionUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(GymTrainSheetState())
    val state: StateFlow<GymTrainSheetState> = _state

    fun load(gymId: Long) {
        val today = training.today()
        val existing = training.summary(com.meteomontana.android.domain.model.gym.GymTrainingPeriod.WEEK, today)
            .sessions.firstOrNull { it.gymId == gymId && it.date == today }
        _state.update { GymTrainSheetState(ascents = existing?.ascents ?: emptyList()) }
    }

    fun adjust(key: String, delta: Int) = _state.update { it.copy(ascents = GymTraining.adjust(it.ascents, key, delta)) }

    /** Apunta la sesión de hoy (si no estaba) y guarda lo hecho. Funciona sin red: primero en el móvil, luego se sube. */
    fun save(gymId: Long, onDone: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { record.execute(gymId, GymSessionSource.MANUAL) }
            val list = _state.value.ascents
            if (list.isNotEmpty()) runCatching { training.saveAscents(gymId, training.today(), list) }
            _state.update { it.copy(saving = false, saved = true) }
            onDone()
        }
    }
}

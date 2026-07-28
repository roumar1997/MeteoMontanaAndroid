package com.meteomontana.android.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.model.OrientationSummary
import com.meteomontana.android.domain.model.SunHours
import com.meteomontana.android.domain.usecase.community.GetGradeVotesUseCase
import com.meteomontana.android.domain.usecase.community.GetOrientationUseCase
import com.meteomontana.android.domain.usecase.community.GetSunHoursUseCase
import com.meteomontana.android.domain.usecase.community.VoteGradeUseCase
import com.meteomontana.android.domain.usecase.community.VoteOrientationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado de la votación comunitaria (C2/C5): orientación por superficie,
 * tira de sol y grado por consenso. Solo use cases (regla DI).
 */
@HiltViewModel
class CommunityVoteViewModel @Inject constructor(
    private val getOrientation: GetOrientationUseCase,
    private val voteOrientationUc: VoteOrientationUseCase,
    private val getSunHours: GetSunHoursUseCase,
    private val getGradeVotes: GetGradeVotesUseCase,
    private val voteGradeUc: VoteGradeUseCase
) : ViewModel() {

    /** Resúmenes por superficie del bloque abierto (photoIndex null = entero). */
    private val _orientation = MutableStateFlow<List<OrientationSummary>>(emptyList())
    val orientation: StateFlow<List<OrientationSummary>> = _orientation

    private val _sun = MutableStateFlow<Map<Int?, SunHours>>(emptyMap())
    val sun: StateFlow<Map<Int?, SunHours>> = _sun

    private val _grade = MutableStateFlow<GradeSummary?>(null)
    val grade: StateFlow<GradeSummary?> = _grade

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun consumeError() { _error.value = null }

    fun loadOrientation(blockId: String) {
        viewModelScope.launch {
            runCatching { getOrientation(blockId) }
                .onSuccess { _orientation.value = it }
        }
    }

    fun loadSun(blockId: String, photoIndex: Int?) {
        viewModelScope.launch {
            runCatching { getSunHours(blockId, photoIndex) }
                .onSuccess { _sun.value = _sun.value + (photoIndex to it) }
        }
    }

    fun voteOrientation(blockId: String, photoIndex: Int?, aspect: String) {
        viewModelScope.launch {
            runCatching { voteOrientationUc(blockId, photoIndex, aspect) }
                .onSuccess {
                    _orientation.value = it
                    loadSun(blockId, photoIndex)   // la tira usa el consenso nuevo
                }
                .onFailure { _error.value = "No se pudo registrar el voto" }
        }
    }

    fun loadGrade(lineId: String) {
        _grade.value = null
        viewModelScope.launch {
            runCatching { getGradeVotes(lineId) }.onSuccess { _grade.value = it }
        }
    }

    fun voteGrade(lineId: String, grade: String) {
        viewModelScope.launch {
            runCatching { voteGradeUc(lineId, grade) }
                .onSuccess { _grade.value = it }
                .onFailure { e ->
                    _error.value = if (e.message?.contains("403") == true ||
                        e.message?.contains("GRADE_VOTE_REQUIRES_JOURNAL") == true)
                        "Solo puede votar el grado quien la tiene en su diario"
                    else "No se pudo registrar el voto"
                }
        }
    }

    fun clearForBlock() {
        _orientation.value = emptyList()
        _sun.value = emptyMap()
        _grade.value = null
    }
}

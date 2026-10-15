package com.meteomontana.android.ui.components

import com.meteomontana.android.util.AppText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.GradeSummary
import com.meteomontana.android.domain.model.HeightGroup
import com.meteomontana.android.domain.model.OrientationSummary
import com.meteomontana.android.domain.model.SunHours
import com.meteomontana.android.domain.usecase.community.GetGradeVotesUseCase
import com.meteomontana.android.domain.usecase.community.GetOrientationUseCase
import com.meteomontana.android.domain.usecase.community.GetSunHoursUseCase
import com.meteomontana.android.domain.usecase.community.RemoveGradeVoteUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyHeightUseCase
import com.meteomontana.android.domain.usecase.profile.SetMyHeightUseCase
import com.meteomontana.android.domain.usecase.community.VoteGradeUseCase
import com.meteomontana.android.domain.usecase.community.VoteOrientationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

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
    private val voteGradeUc: VoteGradeUseCase,
    private val removeGradeVoteUc: RemoveGradeVoteUseCase,
    private val getMyHeightUc: GetMyHeightUseCase,
    private val setMyHeightUc: SetMyHeightUseCase
) : ViewModel() {

    /** Resúmenes por superficie del bloque abierto (photoIndex null = entero). */
    private val _orientation = MutableStateFlow<List<OrientationSummary>>(emptyList())
    val orientation: StateFlow<List<OrientationSummary>> = _orientation

    private val _sun = MutableStateFlow<Map<Int?, SunHours>>(emptyMap())
    val sun: StateFlow<Map<Int?, SunHours>> = _sun

    private val _grade = MutableStateFlow<GradeSummary?>(null)
    val grade: StateFlow<GradeSummary?> = _grade

    /** Lo justo de cada vía para pintar su pastilla de grado sin abrir la hoja: nº de votos y si la altura parte la opinión. */
    private val _gradeBadges = MutableStateFlow<Map<String, GradeBadge>>(emptyMap())
    val gradeBadges: StateFlow<Map<String, GradeBadge>> = _gradeBadges

    /** Mi altura (más o menos de 1,70 m), privada; null = todavía no la he elegido. */
    private val _myHeight = MutableStateFlow<HeightGroup?>(null)
    val myHeight: StateFlow<HeightGroup?> = _myHeight

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
                .onFailure { _error.value = AppText.get(R.string.community_vote_view_model_v4_no_se_pudo_registrar_el) }
        }
    }

    fun loadGrade(lineId: String) {
        _grade.value = null
        viewModelScope.launch {
            runCatching { getGradeVotes(lineId) }.onSuccess { applySummary(it) }
        }
    }

    /** Deja el resumen abierto, la pastilla de esa vía y (si el servidor la manda) mi altura al día. */
    private fun applySummary(g: GradeSummary) {
        _grade.value = g
        _gradeBadges.value = _gradeBadges.value + (g.lineId to GradeBadge.of(g))
        HeightGroup.parse(g.myHeight)?.let { _myHeight.value = it }
    }

    /** Mi altura guardada (para saber si hay que pedirla al votar). */
    fun loadMyHeight() {
        viewModelScope.launch { runCatching { getMyHeightUc() }.onSuccess { if (it != null) _myHeight.value = it } }
    }

    /** La elijo UNA vez (en la hoja de votar o en la mochilita); sus votos ya hechos pasan a contar en su grupo. */
    fun setMyHeight(group: HeightGroup) {
        viewModelScope.launch {
            runCatching { setMyHeightUc(group) }
                .onSuccess {
                    _myHeight.value = it
                    _grade.value?.lineId?.let { id -> loadGrade(id) }
                }
                .onFailure { _error.value = AppText.get(R.string.height_save_error) }
        }
    }

    /** Votos de grado de todas las vías de una piedra, a la vez (una consulta por vía; una piedra tiene pocas). */
    fun loadGradeCounts(lineIds: List<String>) {
        viewModelScope.launch {
            val found = lineIds.map { id ->
                async { runCatching { getGradeVotes(id) }.getOrNull()?.let { id to GradeBadge.of(it) } }
            }.awaitAll().filterNotNull().toMap()
            _gradeBadges.value = _gradeBadges.value + found
        }
    }

    /** Quita MI voto de grado (y recalcula el resumen). */
    fun removeGrade(lineId: String) {
        viewModelScope.launch {
            runCatching { removeGradeVoteUc(lineId) }
                .onSuccess { applySummary(it) }
                .onFailure { _error.value = AppText.get(R.string.grade_vote_remove_error) }
        }
    }

    /** Guarda el voto; [onDone] se llama solo si salió bien (la pantalla de votar se cierra entonces). */
    fun voteGrade(lineId: String, grade: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { voteGradeUc(lineId, grade) }
                .onSuccess { applySummary(it); onDone() }
                .onFailure { e ->
                    _error.value = if (e.message?.contains("403") == true ||
                        e.message?.contains("GRADE_VOTE_REQUIRES_JOURNAL") == true)
                        AppText.get(R.string.community_vote_view_model_v4_solo_puede_votar_el_grado)
                    else AppText.get(R.string.community_vote_view_model_v4_no_se_pudo_registrar_el)
                }
        }
    }

    /** N10: consenso de orientacion consultado EN EL MOMENTO de compartir
     *  (best-effort). No depende de que el resumen ya estuviera cargado. */
    suspend fun fetchOrientationConsensus(blockId: String): String? =
        runCatching { getOrientation(blockId) }.getOrNull()
            ?.firstOrNull { it.photoIndex == null }?.consensus

    /** N10: grado del equipador si DIVERGE del mostrado (consulta directa). */
    suspend fun fetchSetterGradeRef(lineId: String): String? =
        runCatching { getGradeVotes(lineId) }.getOrNull()
            ?.takeIf { it.setterGrade != null && it.setterGrade != it.displayedGrade }
            ?.setterGrade

    fun clearForBlock() {
        _orientation.value = emptyList()
        _sun.value = emptyMap()
        _grade.value = null
    }
}

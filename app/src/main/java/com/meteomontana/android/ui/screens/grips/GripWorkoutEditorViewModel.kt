package com.meteomontana.android.ui.screens.grips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.CreateGripWorkoutRequest
import com.meteomontana.android.data.api.dto.GripWorkoutSetRequest
import com.meteomontana.android.domain.model.GripType
import com.meteomontana.android.domain.usecase.grips.CreateGripWorkoutUseCase
import com.meteomontana.android.domain.usecase.grips.GetGripTypesUseCase
import com.meteomontana.android.domain.usecase.grips.GetGripWorkoutUseCase
import com.meteomontana.android.domain.usecase.grips.UpdateGripWorkoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Un set en edición (estado local, mutable hasta guardar). */
data class EditableSet(
    val reps: Int = 6,
    val workS: Int = 10,
    val restS: Int = 20,
    val gripTypeId: Int? = null,
    val targetMinPct: Float = 10f,
    val targetMaxPct: Float = 30f
)

data class WorkoutEditorState(
    val name: String = "",
    val handMode: String = "POR_REP",     // UNA | POR_SERIE | POR_REP
    val countMode: String = "TIEMPO",      // TIEMPO | PESO
    val restBetweenSetsS: Int = 30,
    val sets: List<EditableSet> = listOf(EditableSet()),
    val gripTypes: List<GripType> = emptyList(),
    val loading: Boolean = true,
    val saved: Boolean = false
)

@HiltViewModel
class GripWorkoutEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGripTypes: GetGripTypesUseCase,
    private val getGripWorkout: GetGripWorkoutUseCase,
    private val createGripWorkout: CreateGripWorkoutUseCase,
    private val updateGripWorkout: UpdateGripWorkoutUseCase
) : ViewModel() {

    private val workoutId: String? = savedStateHandle.get<String>("workoutId")?.takeIf { it != "new" }

    private val _state = MutableStateFlow(WorkoutEditorState())
    val state: StateFlow<WorkoutEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val types = runCatching { getGripTypes() }.getOrDefault(emptyList())
            if (workoutId != null) {
                val w = runCatching { getGripWorkout(workoutId) }.getOrNull()
                if (w != null) {
                    _state.value = WorkoutEditorState(
                        name = w.name, handMode = w.handMode, countMode = w.countMode,
                        restBetweenSetsS = w.restBetweenSetsS,
                        sets = w.sets.map {
                            EditableSet(it.reps, it.workS, it.restS, it.gripTypeId,
                                it.targetMinPct.toFloat(), it.targetMaxPct.toFloat())
                        },
                        gripTypes = types, loading = false
                    )
                    return@launch
                }
            }
            _state.value = _state.value.copy(
                gripTypes = types, loading = false,
                sets = listOf(EditableSet(gripTypeId = types.firstOrNull()?.id))
            )
        }
    }

    fun setName(v: String) { _state.value = _state.value.copy(name = v) }
    fun setHandMode(v: String) { _state.value = _state.value.copy(handMode = v) }
    fun setCountMode(v: String) { _state.value = _state.value.copy(countMode = v) }
    fun setRestBetweenSets(v: Int) { _state.value = _state.value.copy(restBetweenSetsS = v) }

    fun addSet() {
        val last = _state.value.sets.lastOrNull() ?: EditableSet(gripTypeId = _state.value.gripTypes.firstOrNull()?.id)
        _state.value = _state.value.copy(sets = _state.value.sets + last.copy())
    }

    fun removeSet(index: Int) {
        val sets = _state.value.sets.toMutableList()
        if (index in sets.indices && sets.size > 1) { sets.removeAt(index); _state.value = _state.value.copy(sets = sets) }
    }

    fun updateSet(index: Int, transform: (EditableSet) -> EditableSet) {
        val sets = _state.value.sets.toMutableList()
        if (index in sets.indices) { sets[index] = transform(sets[index]); _state.value = _state.value.copy(sets = sets) }
    }

    /** Edición masiva: aplica reps/work/rest a TODOS los sets a la vez. */
    fun applyToAllSets(reps: Int, workS: Int, restS: Int) {
        _state.value = _state.value.copy(sets = _state.value.sets.map { it.copy(reps = reps, workS = workS, restS = restS) })
    }

    fun estimatedDurationSeconds(): Int {
        val s = _state.value
        val perSet = s.sets.sumOf { it.reps * (it.workS + it.restS) }
        val betweenSets = (s.sets.size - 1).coerceAtLeast(0) * s.restBetweenSetsS
        return perSet + betweenSets
    }

    fun save(onDone: (String) -> Unit) {
        val s = _state.value
        if (s.name.isBlank() || s.sets.any { it.gripTypeId == null }) return
        val req = CreateGripWorkoutRequest(
            name = s.name.trim(), handMode = s.handMode, countMode = s.countMode,
            restBetweenSetsS = s.restBetweenSetsS,
            sets = s.sets.mapIndexed { i, set ->
                GripWorkoutSetRequest(
                    sortOrder = i, reps = set.reps, workS = set.workS, restS = set.restS,
                    gripTypeId = set.gripTypeId!!,
                    targetMinPct = set.targetMinPct.toDouble(), targetMaxPct = set.targetMaxPct.toDouble()
                )
            }
        )
        viewModelScope.launch {
            val result = runCatching {
                if (workoutId != null) updateGripWorkout(workoutId, req) else createGripWorkout(req)
            }
            result.getOrNull()?.let {
                _state.value = _state.value.copy(saved = true)
                onDone(it.id)
            }
        }
    }
}

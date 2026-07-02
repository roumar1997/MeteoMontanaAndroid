package com.meteomontana.android.ui.screens.grips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.GripMaxRecord
import com.meteomontana.android.domain.model.GripType
import com.meteomontana.android.domain.model.GripWorkout
import com.meteomontana.android.domain.usecase.grips.GetGripTypesUseCase
import com.meteomontana.android.domain.usecase.grips.GetMyGripMaxesUseCase
import com.meteomontana.android.domain.usecase.grips.GetMyGripWorkoutsUseCase
import com.meteomontana.android.domain.usecase.grips.DeleteGripWorkoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GripsUiState {
    data object Loading : GripsUiState
    data class Success(
        val gripTypes: List<GripType>,
        val maxes: List<GripMaxRecord>,
        val workouts: List<GripWorkout>
    ) : GripsUiState
    data class Error(val message: String) : GripsUiState
}

/** Etiqueta legible para un GripType, p.ej. "4 dedos · Semi-arqueo". */
fun GripType.label(): String {
    val fingers = when (fingerGroup) {
        "FIVE" -> "5 dedos"
        "FOUR" -> "4 dedos"
        "THREE" -> "3 dedos"
        "FRONT_TWO" -> "2 dedos frontales"
        "MID_TWO" -> "2 dedos centrales"
        else -> fingerGroup
    }
    val styleLabel = when (style) {
        "CRIMP" -> "Arqueo"
        "HALF_CRIMP" -> "Semi-arqueo"
        "DRAG" -> "Extensión"
        else -> style
    }
    return "$fingers · $styleLabel"
}

fun handLabel(hand: String): String = if (hand == "LEFT") "IZQ" else "DER"

/** Etiquetas por eje, para los selectores DEDOS × ESTILO (más claros que
 *  una sola fila con las 15 combinaciones). */
fun fingerGroupLabel(fingerGroup: String): String = when (fingerGroup) {
    "FIVE" -> "5 dedos"
    "FOUR" -> "4 dedos"
    "THREE" -> "3 dedos"
    "FRONT_TWO" -> "2 frontales"
    "MID_TWO" -> "2 centrales"
    else -> fingerGroup
}

fun gripStyleLabel(style: String): String = when (style) {
    "CRIMP" -> "Arqueo"
    "HALF_CRIMP" -> "Semi-arqueo"
    "DRAG" -> "Extensión"
    else -> style
}

/** Orden canónico de los ejes (el catálogo del backend puede venir en
 *  cualquier orden). */
val FINGER_GROUP_ORDER = listOf("FIVE", "FOUR", "THREE", "FRONT_TWO", "MID_TWO")
val GRIP_STYLE_ORDER = listOf("CRIMP", "HALF_CRIMP", "DRAG")

@HiltViewModel
class GripsViewModel @Inject constructor(
    private val getGripTypes: GetGripTypesUseCase,
    private val getMyGripMaxes: GetMyGripMaxesUseCase,
    private val getMyGripWorkouts: GetMyGripWorkoutsUseCase,
    private val deleteGripWorkout: DeleteGripWorkoutUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<GripsUiState>(GripsUiState.Loading)
    val state: StateFlow<GripsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                val types = getGripTypes()
                val maxes = getMyGripMaxes()
                val workouts = getMyGripWorkouts()
                GripsUiState.Success(types, maxes, workouts)
            } catch (t: Throwable) {
                GripsUiState.Error(t.message ?: "Error cargando agarres")
            }
        }
    }

    fun deleteWorkout(id: String) {
        viewModelScope.launch {
            runCatching { deleteGripWorkout(id) }
            load()
        }
    }
}

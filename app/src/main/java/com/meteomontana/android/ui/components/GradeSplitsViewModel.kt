package com.meteomontana.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.HeightSplit
import com.meteomontana.android.domain.usecase.community.GetGradeSplitsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Grados partidos por altura de CUALQUIER lista (buscadores, diario, asistente…). Espejo de `GradeSplitsStore` de iOS:
 * las filas avisan con [want] de las vías que enseñan y se piden JUNTAS (una sola consulta cada pocos instantes), sin
 * repetir las ya pedidas.
 */
@HiltViewModel
class GradeSplitsViewModel @Inject constructor(
    private val getGradeSplits: GetGradeSplitsUseCase
) : ViewModel() {

    private val _splits = MutableStateFlow<Map<String, HeightSplit>>(emptyMap())
    val splits: StateFlow<Map<String, HeightSplit>> = _splits

    private val asked = mutableSetOf<String>()
    private val pending = mutableSetOf<String>()
    private var flushing = false

    /** Una fila dice "voy a enseñar esta vía": se junta con las demás y se pide todo de golpe. */
    fun want(lineId: String?) {
        if (lineId.isNullOrBlank() || lineId in asked) return
        pending.add(lineId)
        if (flushing) return
        flushing = true
        viewModelScope.launch {
            delay(250)
            val ids = pending.toList()
            pending.clear()
            flushing = false
            load(ids)
        }
    }

    private suspend fun load(lineIds: List<String>) {
        lineIds.filter { it !in asked }.chunked(500).forEach { chunk ->
            asked.addAll(chunk)
            runCatching { getGradeSplits(chunk) }
                .onSuccess { _splits.value = _splits.value + it }
                .onFailure { asked.removeAll(chunk.toSet()) }   // sin red: se volverá a pedir al reaparecer la fila
        }
    }
}

/** El reparto por altura de esta vía (null si no hay desacuerdo o aún no ha llegado); la pide al aparecer la fila. */
@Composable
fun rememberLineSplit(lineId: String?): HeightSplit? {
    if (lineId.isNullOrBlank()) return null
    val vm: GradeSplitsViewModel = hiltViewModel()
    LaunchedEffect(lineId) { vm.want(lineId) }
    val splits by vm.splits.collectAsStateWithLifecycle()
    return splits[lineId]
}

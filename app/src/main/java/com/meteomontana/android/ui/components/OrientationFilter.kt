package com.meteomontana.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.usecase.community.GetSchoolOrientationsUseCase
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Filtro por ORIENTACIÓN de las piedras de una escuela (buscador del mapa):
 * chips TODAS / N / NE / … con el nº de piedras con ese consenso comunitario.
 * En verano buscas caras norte; en invierno, caras sur — este filtro lo hace
 * de un toque, en la lista Y en los marcadores del mapa.
 *
 * El consenso viene de UNA llamada (GET /schools/{id}/orientations); las
 * piedras sin votos solo aparecen en TODAS.
 */
@HiltViewModel
class OrientationFilterViewModel @Inject constructor(
    private val getSchoolOrientations: GetSchoolOrientationsUseCase
) : ViewModel() {

    /** blockId → aspecto de consenso ("N", "SO"…). Vacío hasta que carga. */
    private val _orientations = MutableStateFlow<Map<String, String>>(emptyMap())
    val orientations: StateFlow<Map<String, String>> = _orientations

    /** Aspecto elegido; null = TODAS. */
    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected

    private var loadedSchoolId: String? = null

    fun load(schoolId: String) {
        if (schoolId == loadedSchoolId) return
        loadedSchoolId = schoolId
        viewModelScope.launch {
            // Sin red o sin votos → mapa vacío: el filtro simplemente no filtra.
            runCatching { getSchoolOrientations(schoolId) }
                .onSuccess { _orientations.value = it }
        }
    }

    fun select(aspect: String?) {
        _selected.value = if (_selected.value == aspect) null else aspect
    }

    /** Ids visibles con el filtro actual (null = sin filtro → todos). */
    fun visibleBlockIds(): Set<String>? {
        val aspect = _selected.value ?: return null
        return _orientations.value.filterValues { it == aspect }.keys
    }

    companion object {
        val ASPECTS = com.meteomontana.android.domain.util.Aspect.ALL
    }
}

/** Chips del filtro (mismo lenguaje discontinuo-terra de "cosa votable"). */
@Composable
internal fun OrientationFilterChips(
    orientations: Map<String, String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    if (orientations.isEmpty()) return
    val counts = orientations.values.groupingBy { it }.eachCount()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        item {
            OrientationChip("TODAS", selected == null, enabled = true) { onSelect(null) }
        }
        OrientationFilterViewModel.ASPECTS.forEach { aspect ->
            val n = counts[aspect] ?: 0
            if (n > 0) item {
                OrientationChip("$aspect · $n", selected == aspect, enabled = true) {
                    onSelect(aspect)
                }
            }
        }
    }
}

@Composable
private fun OrientationChip(label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier
        .background(if (active) Terra else MaterialTheme.colorScheme.surface,
            RoundedCornerShape(6.dp))
        .border(1.dp, if (active) Terra else Terra.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, style = EyebrowTextStyle.copy(fontSize = 10.sp),
            color = if (active) Color.White else Terra)
    }
}

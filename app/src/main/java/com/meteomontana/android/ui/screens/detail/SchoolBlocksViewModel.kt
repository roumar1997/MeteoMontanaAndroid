package com.meteomontana.android.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.schools.ExploreLinesUseCase
import com.meteomontana.android.domain.usecase.schools.LinesFilter
import com.meteomontana.android.domain.usecase.schools.LinesOrder
import com.meteomontana.android.domain.usecase.schools.LinesView
import com.meteomontana.android.domain.usecase.schools.LoadBetaLineIdsUseCase
import com.meteomontana.android.domain.usecase.schools.LoadSchoolLinesUseCase
import com.meteomontana.android.domain.usecase.schools.SchoolLinesExplorer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la pestaña BLOQUES/VÍAS de la ficha de escuela. Vive en el ViewModel (no en la pestaña) para que al ir al
 * mapa o al tiempo y volver no se pierdan los filtros ni se vuelva a bajar la lista. Espejo de `SchoolBlocksExplorerModel`.
 *
 * SOLO guarda estado de pantalla y descarga datos con los use cases. Filtrar, ordenar, el podio y agrupar por sector es
 * lógica de dominio y vive en [SchoolLinesExplorer] (shared, con tests): iOS y Android hacen lo mismo.
 */
@HiltViewModel
class SchoolBlocksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exploreLines: ExploreLinesUseCase
) : ViewModel() {

    private val schoolId: String = checkNotNull(savedStateHandle["schoolId"])
    private val loadLines = LoadSchoolLinesUseCase(exploreLines)
    private val loadBeta = LoadBetaLineIdsUseCase(exploreLines)

    enum class Order(val shared: LinesOrder) { Repeated(LinesOrder.MOST_REPEATED), Rated(LinesOrder.BEST_RATED) }

    data class UiState(
        val loading: Boolean = true,
        val failed: Boolean = false,
        val all: List<LineSearchHit> = emptyList(),
        val query: String = "",
        val grades: Set<String> = emptySet(),
        /** Alturas de beta marcadas. Vacío = sin filtrar; las dos = cualquier vía con beta. */
        val betas: Set<String> = emptySet(),
        val betaLineIds: Map<String, Set<String>> = emptyMap(),
        val order: Order = Order.Repeated,
        /** Sectores DESPLEGADOS (empiezan todos plegados). La clave de "sin sector" es "". */
        val openSectors: Set<String> = emptySet(),
        /** Panel de filtros (grado, beta, orden): plegado por defecto para que se vea antes la lista. */
        val filtersOpen: Boolean = false
    ) {
        val hasLines: Boolean get() = all.any { it.lineId != null }
        val gradeOptions: List<String> get() = SchoolLinesExplorer.gradeOptions(all)

        /** null = todavía bajando los ids de beta de alguna altura marcada. */
        val view: LinesView?
            get() = SchoolLinesExplorer.view(all, LinesFilter(query, grades, betas, order.shared), betaLineIds)

        /** Cuántos filtros se han tocado (grado, beta, orden distinto del de siempre). */
        val activeCount: Int
            get() = (if (grades.isEmpty()) 0 else 1) + (if (betas.isEmpty()) 0 else 1) + (if (order == Order.Repeated) 0 else 1)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loaded = false

    /** Baja las vías de la escuela UNA vez (los filtros se aplican en local). `force` para reintentar. */
    fun loadIfNeeded(force: Boolean = false) {
        if (loaded && !force && !_state.value.failed) return
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                val lines = loadLines(schoolId)
                loaded = true
                _state.update { it.copy(all = lines, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun clearGrades() = _state.update { it.copy(grades = emptySet()) }
    fun toggleGrade(g: String) = _state.update { it.copy(grades = if (g in it.grades) it.grades - g else it.grades + g) }
    fun setOrder(o: Order) = _state.update { it.copy(order = o) }
    fun toggleFilters() = _state.update { it.copy(filtersOpen = !it.filtersOpen) }
    fun toggleSector(key: String) =
        _state.update { it.copy(openSectors = if (key in it.openSectors) it.openSectors - key else it.openSectors + key) }

    /** Los enlaces de beta viven en el servidor: se piden por altura y se guardan. */
    fun toggleBeta(height: String) {
        val on = height in _state.value.betas
        _state.update { it.copy(betas = if (on) it.betas - height else it.betas + height) }
        if (on || _state.value.betaLineIds.containsKey(height)) return
        viewModelScope.launch {
            try {
                val ids = loadBeta(schoolId, height)
                _state.update { it.copy(betaLineIds = it.betaLineIds + (height to ids)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin conexión a medias: se quita el filtro en vez de dejar la lista vacía sin explicación.
                _state.update { it.copy(betas = it.betas - height) }
            }
        }
    }
}

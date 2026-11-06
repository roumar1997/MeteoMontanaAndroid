package com.meteomontana.android.ui.screens.schools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.LineExploreCriteria
import com.meteomontana.android.domain.model.LineSearchHit
import com.meteomontana.android.domain.usecase.schools.ExploreLinesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lo que la pestaña «Vías/Bloques» toma de la lista de Escuelas: los filtros comunes (estilo, roca, distancia), dónde
 * estás y la nota de cada escuela (para «Mejores condiciones»). Lo pasa la pantalla: así ningún ViewModel depende del otro.
 */
data class ExploreContext(
    val style: StyleFilter = StyleFilter.All,
    val rockTypes: List<String> = emptyList(),
    val maxDistanceKm: Double? = null,
    val lat: Double = 40.4168,
    val lon: Double = -3.7038,
    /** Nota de HOY o del tramo de días elegido, por escuela (-1 = sin nota). */
    val scoreBySchool: Map<String, Int> = emptyMap()
)

/**
 * Pestaña «Vías/Bloques» de Escuelas (docs/BLOCK_SEARCH_DESIGN.md §4.1b): grado, orientación, ALTURA y QUÉ BUSCAS, orden,
 * resultados agrupados por escuela o en lista. Separado de [SchoolListViewModel] (lista de escuelas) el 2026-11-05: cada
 * uno con una sola razón para cambiar. Mismo nombre y reparto en iOS.
 */
@HiltViewModel
class ExploreLinesViewModel @Inject constructor(
    private val exploreLines: ExploreLinesUseCase,
    @ApplicationContext appContext: android.content.Context
) : ViewModel() {

    // El grado mín/máx se recuerda entre sesiones (Álvaro: «que se te queden guardados los últimos que has utilizado»).
    private val prefs = appContext.getSharedPreferences("explore_prefs", android.content.Context.MODE_PRIVATE)

    private val _tab = MutableStateFlow(ExploreTab.Schools)
    val tab: StateFlow<ExploreTab> = _tab.asStateFlow()

    private val _gradeMin = MutableStateFlow(prefs.getString("grade_min", null))
    val gradeMin: StateFlow<String?> = _gradeMin.asStateFlow()
    private val _gradeMax = MutableStateFlow(prefs.getString("grade_max", null))
    val gradeMax: StateFlow<String?> = _gradeMax.asStateFlow()

    private val _orientations = MutableStateFlow<Set<String>>(emptySet())
    val orientations: StateFlow<Set<String>> = _orientations.asStateFlow()

    private val _sortBy = MutableStateFlow(ExploreSortBy.Distance)
    val sortBy: StateFlow<ExploreSortBy> = _sortBy.asStateFlow()

    /** ALTURA: null = todas; "TALL" / "SHORT" = con vídeo de beta o hechas por gente de esa altura (`beta=` del servidor). */
    private val _height = MutableStateFlow<String?>(null)
    val height: StateFlow<String?> = _height.asStateFlow()

    /** QUÉ BUSCAS con la ALTURA: "" vídeo o encadenes, "_VIDEO" solo vídeo, "_SENDS" solo encadenes. */
    private val _heightSource = MutableStateFlow("")
    val heightSource: StateFlow<String> = _heightSource.asStateFlow()

    private val _grouped = MutableStateFlow(true)
    val grouped: StateFlow<Boolean> = _grouped.asStateFlow()

    private val _hits = MutableStateFlow<List<LineSearchHit>>(emptyList())
    val hits: StateFlow<List<LineSearchHit>> = _hits.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _context = MutableStateFlow(ExploreContext())

    fun setTab(tab: ExploreTab) { _tab.value = tab; search() }
    fun setGradeMin(g: String?) { _gradeMin.value = g; prefs.edit().putString("grade_min", g).apply(); search() }
    fun setGradeMax(g: String?) { _gradeMax.value = g; prefs.edit().putString("grade_max", g).apply(); search() }
    fun toggleOrientation(o: String) { _orientations.update { if (o in it) it - o else it + o }; search() }
    /** «Más repetidos» cambia lo que se pide al servidor; los otros dos órdenes se resuelven aquí. */
    fun setSortBy(s: ExploreSortBy) { _sortBy.value = s; search() }
    fun setHeight(h: String?) { _height.value = h; search() }
    fun setHeightSource(s: String) { _heightSource.value = s; search() }
    fun toggleGrouped() { _grouped.value = !_grouped.value }

    /**
     * Cambian los filtros comunes, la ubicación o las notas. Solo se vuelve a pedir al servidor si cambia algo de lo
     * que se le manda (las notas solo reordenan).
     */
    fun setContext(c: ExploreContext) {
        val old = _context.value
        _context.value = c
        if (old.copy(scoreBySchool = emptyMap()) != c.copy(scoreBySchool = emptyMap())) search()
    }

    /** Quita grado y orientación (sin salir de la pestaña): lo usa el «sin resultados» para empezar de cero. */
    fun clearCriteria() {
        _gradeMin.value = null; _gradeMax.value = null; _orientations.value = emptySet()
        prefs.edit().remove("grade_min").remove("grade_max").apply()
        search()
    }

    /** Botón BORRAR de la fila de filtros: además de grado y orientación, altura y orden por defecto. */
    fun clearAll() {
        _height.value = null; _heightSource.value = ""; _sortBy.value = ExploreSortBy.Distance
        clearCriteria()
    }

    private var job: Job? = null

    /** Resultado en vivo, sin botón «ver N vías»: espera ~300 ms al último cambio. */
    private fun search() {
        job?.cancel()
        if (_tab.value != ExploreTab.Blocks) { _hits.value = emptyList(); _loading.value = false; return }
        _loading.value = true
        job = viewModelScope.launch {
            delay(300)
            val c = _context.value
            val criteria = LineExploreCriteria(
                gradeMin = _gradeMin.value, gradeMax = _gradeMax.value,
                discipline = when (c.style) {
                    StyleFilter.Boulder -> "BOULDER"
                    StyleFilter.Via -> "ROUTE"
                    StyleFilter.All -> null
                },
                rockTypes = c.rockTypes.ifEmpty { null },
                schoolIds = null,
                orientations = _orientations.value.toList().ifEmpty { null },
                lat = c.lat, lon = c.lon,
                maxDistanceKm = c.maxDistanceKm,
                // «Mejores condiciones» no lo sabe el servidor (la nota del tiempo vive en la app): se pide por distancia
                // y se reordena en groups/sortedHits. «Más repetidos» SÍ lo ordena el servidor.
                sort = if (_sortBy.value == ExploreSortBy.MostRepeated) "MOST_REPEATED" else "DISTANCE",
                // Siempre: cada resultado enseña quién la ha hecho (por altura) y sus vídeos de beta.
                withRepetitions = true,
                beta = _height.value?.let { it + _heightSource.value }
            )
            _hits.value = runCatching { exploreLines(criteria) }.getOrDefault(emptyList())
            _loading.value = false
        }
    }

    private fun score(schoolId: String) = _context.value.scoreBySchool[schoolId] ?: -1

    /** Agrupados por escuela en el orden del servidor (distancia), salvo «Mejores condiciones», que ordena por nota. */
    val groups: StateFlow<List<ExploreGroup>> =
        combine(_hits, _sortBy, _context) { hits, sortBy, _ ->
            val order = mutableListOf<String>()
            val byId = mutableMapOf<String, MutableList<LineSearchHit>>()
            for (h in hits) {
                if (h.schoolId !in byId) { byId[h.schoolId] = mutableListOf(); order.add(h.schoolId) }
                byId.getValue(h.schoolId).add(h)
            }
            val groups = order.map { id -> ExploreGroup(id, byId[id]?.firstOrNull()?.schoolName.orEmpty(), byId[id].orEmpty()) }
            if (sortBy == ExploreSortBy.BestConditions) groups.sortedByDescending { score(it.schoolId) } else groups
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Lista plana (sin agrupar) con el mismo criterio de orden. */
    val sortedHits: StateFlow<List<LineSearchHit>> =
        combine(_hits, _sortBy, _context) { hits, sortBy, _ ->
            if (sortBy == ExploreSortBy.BestConditions) hits.sortedByDescending { score(it.schoolId) } else hits
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

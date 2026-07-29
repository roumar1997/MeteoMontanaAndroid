package com.meteomontana.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.JournalSession
import com.meteomontana.android.domain.usecase.journal.GetMyJournalUseCase
import com.meteomontana.android.domain.usecase.journal.JournalStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MIS ESTADÍSTICAS (C4): baja el diario una vez y filtra/calcula en local con
 * JournalStatsCalculator (shared, testeado). Sin backend nuevo.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getMyJournal: GetMyJournalUseCase
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val discipline: String = "BOULDER",
        val year: String? = null,          // null = todo
        val month: String? = null,         // "01".."12"; solo con año concreto
        val day: String? = null,           // "yyyy-MM-dd": estadísticas de UN día
        val availableYears: List<String> = emptyList(),
        val summary: JournalStatsCalculator.Summary? = null,
        val progression: JournalStatsCalculator.Progression? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var entries: List<JournalSession> = emptyList()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            runCatching { getMyJournal() }
                .onSuccess { entries = it; recompute() }
                .onFailure { _state.value = _state.value.copy(loading = false) }
        }
    }

    fun setDiscipline(d: String) { _state.value = _state.value.copy(discipline = d); recompute() }
    fun setYear(y: String?) { _state.value = _state.value.copy(year = y, month = null, day = null); recompute() }
    fun setMonth(m: String?) { _state.value = _state.value.copy(month = m, day = null); recompute() }
    /** S4: estadísticas de UN día concreto (desde DÍAS DE ROCA). */
    fun setDay(d: String?) { _state.value = _state.value.copy(day = d); recompute() }

    /** N7: dias de roca del filtro actual, recientes primero, con nº de ascensos. */
    fun daysWithCounts(): List<Pair<String, Int>> =
        currentFiltered().groupingBy { it.date }.eachCount()
            .toList().sortedByDescending { it.first }

    /** Un día de roca pulsado: qué ascensos hiciste ESE día. */
    fun entriesForDay(date: String): List<JournalSession> =
        currentFiltered().filter { it.date == date }

    /** Fila de la pirámide pulsable: vías de ESE grado (únicas, recientes primero). */
    fun entriesForGrade(grade: String): List<JournalSession> =
        JournalStatsCalculator.entriesForGrade(currentFiltered(), grade)

    /** Fila de escuela desplegable: vías de ESA escuela, del grado más duro abajo. */
    fun entriesForSchool(school: String): List<JournalSession> =
        JournalStatsCalculator.entriesForSchool(currentFiltered(), school)

    private fun currentFiltered(): List<JournalSession> {
        val st = _state.value
        var filtered = JournalStatsCalculator.filter(entries, st.discipline, st.year)
        st.month?.let { m -> filtered = filtered.filter { it.date.substring(5, 7) == m } }
        st.day?.let { d -> filtered = filtered.filter { it.date == d } }
        return filtered
    }

    private fun recompute() {
        val st = _state.value
        val today = java.time.LocalDate.now().toString()
        var filtered = JournalStatsCalculator.filter(entries, st.discipline, st.year)
        st.month?.let { m -> filtered = filtered.filter { it.date.substring(5, 7) == m } }
        st.day?.let { d -> filtered = filtered.filter { it.date == d } }
        _state.value = st.copy(
            loading = false,
            availableYears = JournalStatsCalculator.availableYears(entries),
            summary = JournalStatsCalculator.summary(filtered, entries, today),
            progression = JournalStatsCalculator.progression(filtered, today)
        )
    }
}

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
    fun setYear(y: String?) { _state.value = _state.value.copy(year = y, month = null); recompute() }
    fun setMonth(m: String?) { _state.value = _state.value.copy(month = m); recompute() }

    private fun recompute() {
        val st = _state.value
        val today = java.time.LocalDate.now().toString()
        var filtered = JournalStatsCalculator.filter(entries, st.discipline, st.year)
        st.month?.let { m -> filtered = filtered.filter { it.date.substring(5, 7) == m } }
        _state.value = st.copy(
            loading = false,
            availableYears = JournalStatsCalculator.availableYears(entries),
            summary = JournalStatsCalculator.summary(filtered, entries, today),
            progression = JournalStatsCalculator.progression(filtered, today)
        )
    }
}
